package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
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
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
 * {@code ENROLLMENT} concept on the registration date) through
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
 * (409, {@link ProgramAdmissionConfigNotOpenException}) — its ticket-sales
 * window is open;</li>
 * <li>a non-null {@code outreachChannelId} must resolve (404,
 * {@code OutreachChannelNotFoundException}) and a non-null
 * {@code schoolTypeId} must resolve (404,
 * {@code HighSchoolTypeNotFoundException}).</li>
 * </ol>
 */
public class RegisterCandidateUseCaseImpl implements RegisterCandidateUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final OutreachChannelRepository outreachChannelRepository;

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	private final LocalDate registrationDate;

	private final int paymentDeadlineDays;

	public RegisterCandidateUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, OutreachChannelRepository outreachChannelRepository,
			HighSchoolTypeRepository highSchoolTypeRepository, LocalDate registrationDate, int paymentDeadlineDays) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.outreachChannelRepository = outreachChannelRepository;
		this.highSchoolTypeRepository = highSchoolTypeRepository;
		this.registrationDate = registrationDate;
		this.paymentDeadlineDays = paymentDeadlineDays;
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
				fichaAmount, generateReference(savedCandidate.getFolio()),
				registrationDate.plusDays(paymentDeadlineDays));
		admissionPaymentRepository.save(payment);

		return toResult(savedCandidate, payment);
	}

	private ProgramAdmissionConfigQueryPort.AdmissionConfigInfo validate(RegisterCandidateCommand command) {
		if (candidatePersonRepository.findByCurp(command.datosGenerales().curp()).isPresent()) {
			throw new CandidateAlreadyExistsException(
					"Ya existe una persona registrada con el CURP: " + command.datosGenerales().curp());
		}

		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(command.seleccionCarrera().admissionConfigId())
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + command.seleccionCarrera().admissionConfigId()));
		if (config.status() != ProgramAdmissionConfigStatus.OPEN) {
			throw new ProgramAdmissionConfigNotOpenException(
					"La configuración de admisión no está abierta: " + config.id());
		}

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

	private CandidateRegistrationResult toResult(Candidate candidate, AdmissionPayment payment) {
		return new CandidateRegistrationResult(candidate.getId(), candidate.getPersonId(),
				candidate.getAdmissionConfigId(), candidate.getFolio(), candidate.getStatus(),
				candidate.isLlaveMxVerified(), candidate.getRegisteredAt(), candidate.isFirstChoice(),
				candidate.getOutreachChannelId(), candidate.isEnabledForInduction(),
				new FichaPayment(payment.getReferenceNumber(), payment.getAmount(), payment.getPaymentDeadline(),
						payment.getPaymentStatus()));
	}
}