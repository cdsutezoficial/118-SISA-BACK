package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotOpenException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigSalesClosedException;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers a candidate's admission ticket ("ficha de admisión") by
 * persisting the applicant's {@code Person} (with its 5 child profiles —
 * {@code Address}, {@code HealthProfile}, {@code DiversityProfile},
 * {@code EmploymentInfo}, {@code HighSchoolBackground}, saved by cascade)
 * plus the new {@code Candidate} row, in one transaction (design:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}; plan:
 * {@code docs/plans/sisa-candidate-ficha.md}).
 *
 * <p>API users land here through {@code CandidateController#register}; string
 * → enum mapping for frontend payloads (sexo "Femenino"/"Masculino",
 * estadoCivil "Soltero/a", tipoTrabajo "Tiempo completo") happens in the web
 * layer, NOT here — this command already speaks {@code Gender}/
 * {@code MaritalStatus}/{@code EmploymentType} and parses dates/hours.
 *
 * <p>The ficha amount is NOT static: it is resolved from the
 * {@code PaymentConcept} catalog (the program's {@code ACTIVE}
 * {@code ADMISSION} concept on the registration date) through
 * {@link FichaAmountResolver}, which owns the strict rule (exactly one active
 * concept: zero means the ficha cannot be priced → 404
 * {@link FichaPaymentConceptNotFoundException}, more than one is ambiguous → 409
 * {@link AmbiguousFichaPaymentConceptException}).
 *
 * <p>Validations, in order:
 * <ol>
 * <li>the {@code curp} must not already belong to a {@code Person}
 * (duplicate candidate / prior registration / conflicting staff row) —
 * 409, {@link CandidateAlreadyExistsException};</li>
 * <li>the chosen {@code ProgramAdmissionConfig} must exist (404,
 * {@link ProgramAdmissionConfigNotFoundException}) and be {@code OPEN}
 * (409, {@link ProgramAdmissionConfigNotOpenException}) — the staff-controlled
 * toggle;</li>
 * <li>{@code now} must fall inside {@code opensAt}/{@code closesAt} (409,
 * {@link ProgramAdmissionConfigSalesClosedException}). Together with the toggle
 * above, these are the two things that decide whether a registration is
 * accepted. <b>The quota is not one of them</b>: {@code maxCandidates} caps
 * fichas sold, and it is enforced at the checkout
 * ({@code CheckoutSlotClaimer#claim}), not here — see the note on the removed
 * check below;</li>
 * <li>a non-null {@code outreachChannelId} must resolve (404,
 * {@code OutreachChannelNotFoundException}) and a non-null
 * {@code schoolTypeId} must resolve (404,
 * {@code HighSchoolTypeNotFoundException}).</li>
 * </ol>
 */
public class RegisterCandidateUseCaseImpl implements RegisterCandidateUseCase {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final OutreachChannelRepository outreachChannelRepository;

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	private final LocalDate registrationDate;

	/**
	 * The sales-window and quota checks compare against <em>now</em>, so the clock
	 * is injected rather than read from a static: a {@code LocalDate.now()} captured
	 * when the bean was built (as {@code registrationDate} still is) would freeze
	 * the window at application startup, which in dev hides the bug and in
	 * production silently sells tickets outside the dates on screen.
	 */
	private final Clock clock;

	/**
	 * Payment-window length in days (§1.9). This is the <em>same</em> value the
	 * daily expiry sweep reads, so the lock's notion of "this old ficha is still
	 * alive" cannot drift from the sweep's notion of "this ficha expired" by a
	 * configuration change applied to only one of them.
	 */
	private final int fichaDeadlineDays;

	public RegisterCandidateUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, OutreachChannelRepository outreachChannelRepository,
			HighSchoolTypeRepository highSchoolTypeRepository, LocalDate registrationDate, Clock clock,
			int fichaDeadlineDays) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.outreachChannelRepository = outreachChannelRepository;
		this.highSchoolTypeRepository = highSchoolTypeRepository;
		this.registrationDate = registrationDate;
		this.clock = clock;
		this.fichaDeadlineDays = fichaDeadlineDays;
	}

	@Override
	@Transactional
	public CandidateRegistrationResult register(RegisterCandidateCommand command) {
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = validate(command);

		Person savedPerson = candidatePersonRepository.save(buildPerson(command));

		Candidate candidate = new Candidate(savedPerson.getId(), command.seleccionCarrera().admissionConfigId(),
				generateFolio(), command.llaveMxVerified(), command.seleccionCarrera().isFirstChoice(),
				command.seleccionCarrera().outreachChannelId());
		Candidate savedCandidate = candidateRepository.save(candidate);

		BigDecimal fichaAmount = fichaAmountResolver.resolve(config.programId(), registrationDate).amount();
		AdmissionPayment payment = new AdmissionPayment(savedCandidate.getId(), AdmissionPaymentConcept.ADMISSION_FICHA,
				fichaAmount, generateReference(savedCandidate.getFolio()), registrationDeadline(config));
		admissionPaymentRepository.save(payment);

		return toResult(savedCandidate, payment, config);
	}

	/**
	 * The ficha's registration deadline is the day the sales window closes, not
	 * "ten days after today".
	 *
	 * <p>The old {@code registrationDate.plusDays(paymentDeadlineDays)} had no
	 * relationship to anything the applicant could see or act on: with the period
	 * closing 30/09 it printed 06/10, a date that contradicted both catalogs and
	 * was enforced by nothing. Deriving it from {@code closesAt} means the number
	 * on the ticket is the same boundary that
	 * {@link #validateSalesWindow(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo)}
	 * refuses registrations past, so the two can no longer disagree.
	 *
	 * <p>Converted through the clock's zone, not the server default: {@code
	 * closesAt} is an {@link Instant}, and which calendar day it lands on is a
	 * question about the university's day, not the host's. A window closing at
	 * 05:00 UTC is 30/09 in Emiliano Zapata and 01/10 in Madrid — a host east of
	 * UTC-6 would otherwise label the ficha with a day the applicant never saw on
	 * the form.
	 *
	 * <p>No null handling on purpose: {@code closes_at} is {@code NOT NULL}, and
	 * {@link #validateSalesWindow} already dereferences it, so a missing boundary
	 * cannot reach here. Inventing a fallback date for a case that cannot happen
	 * would only mean a wrong date printed on a real ticket.
	 */
	private LocalDate registrationDeadline(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config) {
		return config.closesAt().atZone(clock.getZone()).toLocalDate();
	}

	private ProgramAdmissionConfigQueryPort.AdmissionConfigInfo validate(RegisterCandidateCommand command) {
		validateCurpHasNoLiveFicha(command.datosGenerales().curp());

		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(command.seleccionCarrera().admissionConfigId())
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + command.seleccionCarrera().admissionConfigId()));
		if (config.status() != ProgramAdmissionConfigStatus.OPEN) {
			throw new ProgramAdmissionConfigNotOpenException(
					"La configuración de admisión no está abierta: " + config.id());
		}

		validateSalesWindow(config);

		if (command.seleccionCarrera().outreachChannelId() != null
				&& outreachChannelRepository.findById(command.seleccionCarrera().outreachChannelId()).isEmpty()) {
			throw new OutreachChannelNotFoundException(
					"No existe el canal de difusión: " + command.seleccionCarrera().outreachChannelId());
		}

		if (command.antecedentesEscolares().schoolTypeId() != null
				&& highSchoolTypeRepository.findById(command.antecedentesEscolares().schoolTypeId()).isEmpty()) {
			throw new HighSchoolTypeNotFoundException(
					"No existe el tipo de preparatoria: " + command.antecedentesEscolares().schoolTypeId());
		}

		return config;
	}

	/**
	 * The CURP lock is on the <em>ficha</em>, not on the person (§1.10): a person
	 * may have registered before and come back, so the mere existence of a
	 * {@code Person} with this CURP proves nothing. What blocks a new registration
	 * is a <em>live</em> ficha:
	 *
	 * <ul>
	 * <li>{@code PAYMENT_EXPIRED} — the ficha lapsed without payment, the person is
	 * free again;</li>
	 * <li>{@code REGISTERED} past its payment window — the same fact stated by the
	 * clock, before the daily sweep has had a chance to write it down. Reading the
	 * date here keeps the lock correct in the gap between the deadline and the
	 * next 00:10 run;</li>
	 * <li>anything else ({@code PAID}, {@code EXAM_TAKEN}, …) — the person is
	 * already inside the process and blocks for good.</li>
	 * </ul>
	 *
	 * <p>A person with no fichas at all (a staff {@code Person} row, or a person
	 * created by another module) is therefore free to register; that is the
	 * intended change, and the reason the old {@code findByCurp().isPresent()}
	 * guard is gone.
	 */
	private void validateCurpHasNoLiveFicha(String curp) {
		Optional<Person> person = candidatePersonRepository.findByCurp(curp);
		if (person.isEmpty()) {
			return;
		}
		LocalDate today = LocalDate.now(clock);
		for (Candidate previous : candidateRepository.findAllByPersonId(person.get().getId())) {
			if (previous.getStatus() == CandidateStatus.PAYMENT_EXPIRED) {
				continue;
			}
			if (previous.getStatus() == CandidateStatus.REGISTERED
					&& today.isAfter(previous.paymentDeadline(clock.getZone(), fichaDeadlineDays))) {
				continue;
			}
			throw new CandidateAlreadyExistsException(previous.getStatus() == CandidateStatus.REGISTERED
					? "Este CURP ya tiene una ficha vigente. Podrás registrarte de nuevo si esa ficha vence sin pago."
					: "Este CURP ya tiene una ficha en el proceso de admisión. No es posible registrar una nueva.");
		}
	}

	/**
	 * {@code opensAt}/{@code closesAt} are the two dates the Configuración de
	 * Admisión screen puts next to a "Venta de fichas" label, so honouring them is
	 * the whole point: without this the only thing gating a registration is the
	 * {@code status} toggle, which staff flip by hand and which knows nothing about
	 * the calendar.
	 *
	 * <p>Boundaries are inclusive on both ends — a sale that closes at 18:00 on the
	 * 30th is still sellable at 18:00 on the 30th. The message names the boundary
	 * that was missed and carries no identifiers, so the applicant can be shown it
	 * as-is.
	 */
	private void validateSalesWindow(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config) {
		Instant now = clock.instant();
		if (now.isBefore(config.opensAt())) {
			throw new ProgramAdmissionConfigSalesClosedException("La venta de fichas para esta carrera abre el "
					+ formatDate(config.opensAt()) + ".");
		}
		if (now.isAfter(config.closesAt())) {
			throw new ProgramAdmissionConfigSalesClosedException("La venta de fichas para esta carrera cerró el "
					+ formatDate(config.closesAt()) + ".");
		}
	}

	/**
	 * There is deliberately no quota check here.
	 *
	 * <p>This used to count paid fichas and refuse the registration, and it was
	 * wrong in the way that matters: it read the quota days before the thing it was
	 * protecting. A candidate who registered on Monday and clicked "pay" on Friday
	 * was checked on Monday, against a count that could not include a payment they
	 * had not made yet. Everybody who read the counter the same way got through,
	 * and the overshoot only showed up at the bank.
	 *
	 * <p>The rule now lives at the checkout instead
	 * ({@code CheckoutSlotClaimer#claim}), which is where the money is actually
	 * requested and the last point a refusal is still free. Registration stays open
	 * for as many people as want it: "pueden registrarse 100 pero solo pagan 15"
	 * means the cap is on fichas <em>sold</em>, and a registration nobody pays for
	 * is not a ficha sold.
	 */
	/**
	 * Renders an instant as the calendar date the applicant is looking at, in the
	 * clock's own zone — the same zone the comparison above was made in, so the
	 * date we quote and the date we enforced cannot disagree by a day. The pattern
	 * is explicit rather than locale-defaulted because this string is shown on
	 * screen, where the rest of the app already uses {@code dd/MM/yyyy}.
	 */
	private String formatDate(Instant instant) {
		return DATE_FORMAT.format(instant.atZone(clock.getZone()).toLocalDate());
	}

	private Person buildPerson(RegisterCandidateCommand command) {
		DatosGenerales datos = command.datosGenerales();
		Person person = new Person(datos.curp(), datos.firstName(), datos.lastName1(), datos.lastName2(), null);
		person.setPersonalEmail(command.contacto().personalEmail());
		person.setHomePhone(command.contacto().homePhone());
		person.setMobilePhone(command.contacto().mobilePhone());
		person.setBirthDate(datos.birthDate());
		person.setGender(datos.gender());
		person.setNationality(datos.nationality());
		person.setBirthStateId(datos.birthStateId());
		person.setBirthMunicipalityId(datos.birthMunicipalityId());
		person.setBirthForeignState(datos.birthForeignState());
		person.setBirthForeignMunicipality(datos.birthForeignMunicipality());
		person.setMaritalStatus(datos.maritalStatus());
		person.setNativeLanguage(datos.nativeLanguage());
		person.setHasChildren(datos.hasChildren());
		person.setMonthlyFamilyIncome(command.ingresos().monthlyFamilyIncome());
		person.setAddress(toAddress(command.domicilio()));
		person.setHealthProfile(toHealthProfile(command.informacionComplementaria()));
		person.setDiversityProfile(toDiversityProfile(command.informacionComplementaria()));
		person.setEmploymentInfo(toEmploymentInfo(command.ingresos()));
		person.setHighSchoolBackground(toHighSchoolBackground(command.antecedentesEscolares()));
		return person;
	}

	private static Address toAddress(Domicilio domicilio) {
		if (domicilio == null) {
			return null;
		}
		return new Address(domicilio.street(), domicilio.exteriorNumber(), domicilio.interiorNumber(),
				domicilio.neighborhood(), domicilio.locality(), domicilio.postalCode(), domicilio.stateId(),
				domicilio.municipalityId(), null, null);
	}

	private static HealthProfile toHealthProfile(InformacionComplementaria info) {
		if (info == null) {
			return null;
		}
		return new HealthProfile(info.hasPreexistingCondition(), info.conditionDescription(), info.hasDisability(),
				info.disabilityDescription(), null);
	}

	private static DiversityProfile toDiversityProfile(InformacionComplementaria info) {
		if (info == null) {
			return null;
		}
		return new DiversityProfile(info.parentsSpeakIndigenousLanguage(), info.speaksIndigenousLanguage(),
				info.selfIdentifiesIndigenous(), info.selfIdentifiesNonBinary(), info.belongsToLgbttiqCommunity(),
				info.isAfrodescendant(), info.selfIdentifiesAfrodescendant(), info.parentsIndigenousLanguage(),
				info.indigenousLanguage());
	}

	private static EmploymentInfo toEmploymentInfo(Ingresos ingresos) {
		if (ingresos == null) {
			return null;
		}
		return new EmploymentInfo(ingresos.isEmployed(), ingresos.employmentType(), ingresos.companyName(),
				ingresos.jobTitle(), ingresos.workPhone(), ingresos.monthlyIncome(), ingresos.workStartTime(),
				ingresos.workEndTime());
	}

	private static HighSchoolBackground toHighSchoolBackground(AntecedentesEscolares antecedentes) {
		if (antecedentes == null) {
			return null;
		}
		return new HighSchoolBackground(antecedentes.schoolName(), antecedentes.schoolCity(),
				antecedentes.schoolTypeId(), antecedentes.gpa(), antecedentes.studiedInMexico(),
				antecedentes.schoolStateId(), antecedentes.schoolMunicipalityId(), antecedentes.cct(),
				antecedentes.cctConfirmed(), antecedentes.foreignCountry(), null, null, null);
	}

	/**
	 * {@code ADM-{year}-{seq}:06d}. {@code seq = count + 1} for the current
	 * calendar-year {@code "ADM-{year}-"} — see
	 * {@link CandidateRepository#countByFolioStartingWith}.
	 */
	private String generateFolio() {
		String prefix = String.format(Locale.ROOT, "ADM-%d-", Year.now().getValue());
		long seq = candidateRepository.countByFolioStartingWith(prefix) + 1;
		return String.format(Locale.ROOT, "%s%06d", prefix, seq);
	}

	/**
	 * {@code REF-{yyyyMMdd}-{folioSeq}} — deterministic, derived from the
	 * candidate's folio sequence (matches the frontend mock format
	 * {@code REF-yyyyMMdd-XXXXXX} so the same reference renders end-to-end).
	 */
	private String generateReference(String folio) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		return String.format("REF-%s-%s", today, seq);
	}

	/**
	 * Both dates travel back to the screen because they answer two different
	 * questions and the old single {@code deadline} answered neither: when the
	 * registration window shut (a snapshot, and already past by definition once
	 * the applicant is reading this) and when the payment window shuts (live, and
	 * the only one that still constrains anything).
	 */
	private CandidateRegistrationResult toResult(Candidate candidate, AdmissionPayment payment,
			ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config) {
		return new CandidateRegistrationResult(candidate.getId(), candidate.getPersonId(),
				candidate.getAdmissionConfigId(), candidate.getFolio(), candidate.getStatus(),
				candidate.isLlaveMxVerified(), candidate.getRegisteredAt(), candidate.isFirstChoice(),
				candidate.getOutreachChannelId(), candidate.isEnabledForInduction(),
				new FichaPayment(payment.getReferenceNumber(), payment.getAmount(), payment.getRegistrationDeadline(),
						payment.getPaymentStatus(), fichaAmountResolver.paymentClosesOn(config.programId())));
	}
}