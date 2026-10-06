package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.CandidateRegistrationResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigSalesClosedException;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseImplTest {

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();

	/**
	 * A second admission process, already closed. Used to date a previous ficha by
	 * the process that actually issued it instead of by the one open for sales.
	 */
	private static final UUID CLOSED_CONFIG_ID = UUID.randomUUID();
	private static final UUID PROGRAM_ID = UUID.randomUUID();
	private static final UUID CHANNEL_ID = UUID.randomUUID();
	private static final UUID SCHOOL_TYPE_ID = UUID.randomUUID();
	private static final BigDecimal CONCEPT_COST = new BigDecimal("1578.00");

	/**
	 * The tuition concept's {@code available_until}, i.e. the date that actually
	 * governs the payment. Deliberately a different day from
	 * {@link #WINDOW_CLOSE}'s local date: if both dates came out equal, a test
	 * could not tell which window a field was reporting.
	 */
	private static final LocalDate PAYMENT_CLOSES_ON = LocalDate.of(2026, 9, 30);

	/**
	 * The sales window and the quota are the two rules that used to live only on
	 * screen, so they are tested against a clock we control rather than
	 * {@code Instant.now()}: "today" would otherwise make every test pass or fail
	 * depending on the day CI runs, and the boundary cases (exactly
	 * {@code opensAt}, exactly {@code closesAt}) would be untestable.
	 */
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final Instant NOW = Instant.parse("2026-09-25T18:00:00Z");

	private static final LocalDate REGISTRATION_DATE = LocalDate.of(2026, 9, 25);

	/**
	 * Anchored mid-day on purpose. The boundaries are stored as UTC instants but
	 * the message the applicant reads is a local date, so a boundary sitting on
	 * midnight UTC renders as the <em>previous</em> day in this zone — real, and
	 * covered by {@link #register_namesTheMissingBoundaryInTheClocksZone()}, but
	 * noise in every other window test. Midday makes the rendered date obvious.
	 */
	private static final Instant WINDOW_OPEN = Instant.parse("2026-09-01T15:00:00Z");

	/** 30/09 23:00 in {@link #ZONE} — "closes on the 30th", not "closes at midnight". */
	private static final Instant WINDOW_CLOSE = Instant.parse("2026-10-01T05:00:00Z");

	private static final int MAX_CANDIDATES = 40;

	private static final int FICHA_DEADLINE_DAYS = 10;

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	@Mock
	private OutreachChannelRepository outreachChannelRepository;

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private RegisterCandidateUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = useCaseAt(NOW);
	}

	/**
	 * Same use case, different "now". {@code registrationDate} stays put so the
	 * concept amount and the registration deadline are unaffected by which
	 * instant a given test is exercising.
	 */
	private RegisterCandidateUseCaseImpl useCaseAt(Instant now) {
		return useCaseAt(now, ZONE);
	}

	private RegisterCandidateUseCaseImpl useCaseAt(Instant now, ZoneId zone) {
		return new RegisterCandidateUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, fichaAmountResolver,
				outreachChannelRepository, highSchoolTypeRepository, REGISTRATION_DATE, Clock.fixed(now, zone),
				FICHA_DEADLINE_DAYS);
	}

	private static AdmissionConfigInfo config(Instant opensAt, Instant closesAt, int maxCandidates) {
		return configIn(ADMISSION_CONFIG_ID, opensAt, closesAt, maxCandidates);
	}

	/**
	 * Same config under a different id, for the tests where a previous ficha
	 * belongs to a different admission process than the one being applied to —
	 * otherwise the lock would date it by the process currently open for sales.
	 */
	private static AdmissionConfigInfo configIn(UUID configId, Instant opensAt, Instant closesAt, int maxCandidates) {
		return new AdmissionConfigInfo(configId, ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID,
				"Ingeniería en Sistemas", null, null, null, opensAt, closesAt, maxCandidates);
	}

	/**
	 * Just the two lookups that happen before the sales window and the quota are
	 * checked. The rejection tests use only this: stubbing the channel / school
	 * type / concept amount here would leave unused stubs behind, which
	 * {@code MockitoExtension}'s strict mode treats as a failure.
	 */
	private void stubConfig(AdmissionConfigInfo config) {
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.empty());
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.of(config));
	}

	/**
	 * Everything a registration needs beyond the two new rules: the CURP is free,
	 * the config is returned, the reference data resolves, and the repository hands
	 * back the saved rows. Window/quota tests call this and then override only the
	 * one stub they are actually about.
	 */
	private void stubAcceptedRegistration(AdmissionConfigInfo config) {
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.empty());
		stubAcceptedRegistrationStubs(config);
	}

	/**
	 * Same as {@link #stubAcceptedRegistration}, but the CURP resolves to a person
	 * who already has a ficha, so the live-ficha lock is exercised. Whether the
	 * registration goes through is then decided by that ficha's status and date.
	 */
	private void stubAcceptedRegistrationWithCurpAlreadyUsed(AdmissionConfigInfo config, Candidate previousFicha) {
		Person existing = personWithId();
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(existing));
		when(candidateRepository.findAllByPersonId(existing.getId())).thenReturn(List.of(previousFicha));
		stubAcceptedRegistrationStubs(config);
	}

	private static Person personWithId() {
		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		ReflectionTestUtils.setField(person, "id", UUID.randomUUID());
		return person;
	}

	/** The config lookup alone, shared by every accepted-registration stub. */
	private void stubConfigLookup(AdmissionConfigInfo config) {
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.of(config));
	}

	private void stubAcceptedRegistrationStubs(AdmissionConfigInfo config) {
		Person person = personWithId();
		when(outreachChannelRepository.findById(CHANNEL_ID))
				.thenReturn(Optional.of(new OutreachChannel("Portal de admisión")));
		when(highSchoolTypeRepository.findById(SCHOOL_TYPE_ID))
				.thenReturn(Optional.of(new mx.edu.utez.sisa.admission.domain.model.HighSchoolType("Bachillerato")));
		when(candidatePersonRepository.save(any())).thenReturn(person);
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubConfigLookup(config);
		when(fichaAmountResolver.resolve(PROGRAM_ID, REGISTRATION_DATE))
				.thenReturn(new FichaAmountResolver.FichaAmount(CONCEPT_COST, "Inscripción"));
		when(fichaAmountResolver.paymentClosesOn(PROGRAM_ID)).thenReturn(PAYMENT_CLOSES_ON);
	}

	@Test
	void register_generatesPaymentWithReferenceAmountAndBothWindowDates() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		CandidateRegistrationResult result = useCase.register(command());

		assertThat(result.status()).isEqualTo(CandidateStatus.REGISTERED);
		assertThat(result.payment()).isNotNull();
		// The reference is the folio behind a prefix and nothing else: no date, no
		// suffix (decisión 11.1). Asserting it against the folio instead of a
		// literal keeps the test honest year-round — the folio is what carries the
		// year, not the machine's clock — and proves there is no date left in it.
		assertThat(result.payment().referenceNumber()).isEqualTo("REF-" + result.folio());
		assertThat(result.payment().amount()).isEqualByComparingTo(CONCEPT_COST);
		assertThat(result.payment().paymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);
		// The registration deadline is closes_at read in the admission zone, NOT
		// "registration + 10 days". WINDOW_CLOSE is 2026-10-01T05:00Z, which is
		// still 30/09 in Emiliano Zapata.
		assertThat(result.payment().registrationDeadline()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(result.payment().paymentClosesOn()).isEqualTo(PAYMENT_CLOSES_ON);

		ArgumentCaptor<AdmissionPayment> paymentCaptor = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(paymentCaptor.capture());
		AdmissionPayment saved = paymentCaptor.getValue();
		assertThat(saved.getConcept()).isEqualTo(AdmissionPaymentConcept.ADMISSION_FICHA);
		assertThat(saved.getReferenceNumber()).isEqualTo(result.payment().referenceNumber());
		assertThat(saved.getPaymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);
		assertThat(saved.getRegistrationDeadline()).isEqualTo(result.payment().registrationDeadline());
	}

	/**
	 * The whole point of the block, stated as a test: a period closing on the
	 * 30th must not tell the applicant the 6th of the next month.
	 *
	 * <p>There is no longer a "10 days" anywhere to drift from, so the two dates
	 * are pinned to the catalog and a change in either boundary moves exactly the
	 * field it names.
	 */
	@Test
	void register_neverReportsADateLaterThanTheSalesWindowItRegisteredUnder() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		CandidateRegistrationResult result = useCase.register(command());

		assertThat(result.payment().registrationDeadline())
				.isBeforeOrEqualTo(WINDOW_CLOSE.atZone(ZONE).toLocalDate())
				.isEqualTo(WINDOW_CLOSE.atZone(ZONE).toLocalDate());
	}

	/**
	 * The conversion follows the clock's zone, so a server that is not on UTEZ
	 * time still stamps the ficha's deadline with the day the applicant saw on the
	 * form.
	 *
	 * <p>{@code WINDOW_CLOSE} is 2026-10-01T05:00Z. That instant is 30/09 in
	 * Emiliano Zapata and 01/10 in Madrid (UTC+2 that day), so the two zones give
	 * two different answers — which is exactly what makes this a real assertion:
	 * if the code read {@code ZoneId.systemDefault()} instead of the clock's zone,
	 * the Madrid run would depend on wherever the JVM happens to be, and this
	 * assertion would not be reproducible.
	 */
	@Test
	void theRegistrationDeadlineIsClosesAtInTheClocksZoneNotTheServers() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		CandidateRegistrationResult inMorelos = useCaseAt(NOW, ZONE).register(command());
		CandidateRegistrationResult inMadrid = useCaseAt(NOW, ZoneId.of("Europe/Madrid")).register(command());

		assertThat(inMorelos.payment().registrationDeadline()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(inMadrid.payment().registrationDeadline()).isEqualTo(LocalDate.of(2026, 10, 1));
	}

	/**
	 * A concept with no closing date must not be turned into one. Inventing a
	 * deadline here is what produced the original contradiction, so a null has to
	 * survive all the way to the response.
	 */
	@Test
	void register_leavesThePaymentWindowUnsetWhenTheConceptHasNoClosingDate() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));
		when(fichaAmountResolver.paymentClosesOn(PROGRAM_ID)).thenReturn(null);

		CandidateRegistrationResult result = useCase.register(command());

		assertThat(result.payment().paymentClosesOn()).isNull();
		// and the registration date is still there, because it is a different fact
		assertThat(result.payment().registrationDeadline()).isEqualTo(LocalDate.of(2026, 9, 30));
	}

	@Test
	void register_rejectsASaleThatHasNotOpenedYet() {
		stubConfig(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));
		RegisterCandidateUseCaseImpl beforeOpening = useCaseAt(WINDOW_OPEN.minusSeconds(1));

		assertThatThrownBy(() -> beforeOpening.register(command()))
				.isInstanceOf(ProgramAdmissionConfigSalesClosedException.class)
				.hasMessageContaining("01/09/2026");
	}

	@Test
	void register_rejectsASaleThatAlreadyClosed() {
		stubConfig(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));
		RegisterCandidateUseCaseImpl afterClosing = useCaseAt(WINDOW_CLOSE.plusSeconds(1));

		assertThatThrownBy(() -> afterClosing.register(command()))
				.isInstanceOf(ProgramAdmissionConfigSalesClosedException.class)
				.hasMessageContaining("30/09/2026");
	}

	@Test
	void register_acceptsTheExactInstantTheSaleOpensAndTheExactInstantItCloses() {
		// Both boundaries are inclusive. "Cierra el 30/09" has to mean through the
		// last instant of the 30th, and "abre el 01/09" has to include midnight —
		// otherwise the picker and this check disagree by one day at each edge.
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		assertThatCode(() -> useCaseAt(WINDOW_OPEN).register(command())).doesNotThrowAnyException();
		assertThatCode(() -> useCaseAt(WINDOW_CLOSE).register(command())).doesNotThrowAnyException();
	}

	/**
	 * The regression this whole block exists for: registration must not look at
	 * the quota at all.
	 *
	 * <p>It used to count paid fichas and refuse on {@code paid >= max}. The check
	 * was correct arithmetic on a stale fact — it ran at registration, while the
	 * slot it was protecting would not be consumed until payment, possibly days
	 * later. Everybody who read the counter the same way got in, so the check
	 * prevented nothing; the overshoot only surfaced at the bank. The count now
	 * happens once, at the checkout, under a row lock.
	 */
	@Test
	void register_neverConsultsTheQuota() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		useCase.register(command());

		verify(admissionPaymentRepository, never()).countPaidByAdmissionConfigId(any());
		verify(admissionPaymentRepository, never()).countOccupiedByConfigId(any(), any(), anyInt());
		verify(admissionPaymentRepository, never()).countOccupiedByConfigIdExcludingCandidate(any(), any(), any(),
				anyInt());
	}

	/**
	 * "Pueden registrarse 100 pero solo pagan 15" — with the registration side of
	 * that sentence spelled out. A career with <em>zero</em> slots still takes new
	 * registrations; refusing here is what used to make the quota look enforced
	 * when it was not.
	 *
	 * <p>Stated as {@code maxCandidates = 0} rather than as a stubbed count of
	 * paid fichas, because the paid count is not consulted at all any more: a stub
	 * returning "15 of 15 sold" would be read by nobody and would only assert that
	 * the mock was never asked — which is the other test's job. Zero slots is a
	 * state the config can genuinely be in, and it survives a future implementation
	 * that starts reading some other counter.
	 */
	@Test
	void register_acceptsEvenWhenTheCareerHasNoSlotsLeft() {
		stubAcceptedRegistration(config(WINDOW_OPEN, WINDOW_CLOSE, 0));

		assertThatCode(() -> useCase.register(command())).doesNotThrowAnyException();
	}

	@Test
	void register_reportsTheClosedWindowBeforeAnythingElse() {
		// A closed sale is the one thing that still refuses a registration, and it
		// must not depend on a count query: the applicant gets the date, and we do
		// not pay for a DB round trip we are about to discard.
		stubConfig(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));
		RegisterCandidateUseCaseImpl afterClosing = useCaseAt(WINDOW_CLOSE.plusSeconds(1));

		assertThatThrownBy(() -> afterClosing.register(command()))
				.isInstanceOf(ProgramAdmissionConfigSalesClosedException.class);

		verify(admissionPaymentRepository, never()).countPaidByAdmissionConfigId(any());
	}

	@Test
	void register_namesTheMissingBoundaryInTheClocksZone() {
		// One stored instant, two clock zones, two different calendar days. The
		// rendered date must come from the clock the comparison used — if it were
		// formatted in a hardcoded zone, one of these two would tell the applicant
		// to come back on a day the rule itself is not looking at.
		Instant opensAt = Instant.parse("2026-09-01T00:00:00Z");
		stubConfig(config(opensAt, WINDOW_CLOSE, MAX_CANDIDATES));
		Instant justBefore = opensAt.minusSeconds(1);

		assertThatThrownBy(() -> useCaseAt(justBefore, ZoneOffset.UTC).register(command()))
				.isInstanceOf(ProgramAdmissionConfigSalesClosedException.class)
				.hasMessageContaining("01/09/2026");

		assertThatThrownBy(() -> useCaseAt(justBefore, ZONE).register(command()))
				.isInstanceOf(ProgramAdmissionConfigSalesClosedException.class)
				.hasMessageContaining("31/08/2026");
	}

	/**
	 * §1.10: the lock is the ficha, not the person. A live ficha (still inside its
	 * payment window) refuses a second registration even though the person exists.
	 */
	@Test
	void register_rejectsAReRegistrationWhenTheCurpStillHasALiveFicha() {
		Candidate live = candidateRegisteredAt(Instant.parse("2026-09-22T18:00:00Z"));
		Person existing = personWithId();
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(existing));
		when(candidateRepository.findAllByPersonId(existing.getId())).thenReturn(List.of(live));

		assertThatThrownBy(() -> useCase.register(command()))
				.isInstanceOf(CandidateAlreadyExistsException.class)
				.hasMessageContaining("ficha vigente");
	}

	@Test
	void register_rejectsAReRegistrationWhenTheCurpAlreadyPaid() {
		Candidate paid = candidateRegisteredAt(Instant.parse("2026-09-01T18:00:00Z"));
		paid.markPaid();
		Person existing = personWithId();
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(existing));
		when(candidateRepository.findAllByPersonId(existing.getId())).thenReturn(List.of(paid));

		assertThatThrownBy(() -> useCase.register(command()))
				.isInstanceOf(CandidateAlreadyExistsException.class)
				.hasMessageContaining("No es posible registrar una nueva");
	}

	/**
	 * §1.10/§1.11: an expired ficha releases the CURP, so the person can register
	 * again.
	 */
	@Test
	void register_allowsAReRegistrationWhenTheOnlyFichaExpired() {
		Candidate expired = candidateRegisteredAt(Instant.parse("2026-09-01T18:00:00Z"));
		expired.markPaymentExpired();
		stubAcceptedRegistrationWithCurpAlreadyUsed(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES), expired);

		CandidateRegistrationResult result = useCase.register(command());

		assertThat(result.status()).isEqualTo(CandidateStatus.REGISTERED);
	}

	/**
	 * The sweep runs at 00:10; between the deadline and that run a ficha is still
	 * {@code REGISTERED} but already dead. The lock reads the date so it does not
	 * keep the CURP hostage until the next scheduled pass.
	 */
	@Test
	void register_allowsAReRegistrationWhenTheRegisteredFichaIsPastItsWindow() {
		Candidate stale = candidateRegisteredAt(Instant.parse("2026-09-05T18:00:00Z"));
		stubAcceptedRegistrationWithCurpAlreadyUsed(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES), stale);

		assertThatCode(() -> useCase.register(command())).doesNotThrowAnyException();
	}

	/**
	 * The other half of the same rule: a process closing also ends the ficha, so a
	 * CURP whose previous ficha belongs to a closed process is free again even
	 * though its own ten days are not up.
	 *
	 * <p>Before this the lock only knew the ficha's own window, so the person was
	 * told to wait for a payment window that had already closed with her process —
	 * and the only thing that would ever release her was the nightly sweep, which
	 * used to agree about this for the wrong reason.
	 */
	@Test
	void register_allowsAReRegistrationWhenTheClosedProcessEndedTheFicha() {
		// Registered on the 20th: her own ten days run to the 30th, so only the
		// closing of her process can end this ficha.
		Candidate fresh = candidateRegisteredIn(CLOSED_CONFIG_ID, Instant.parse("2026-09-20T18:00:00Z"));
		Person existing = personWithId();
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(existing));
		when(candidateRepository.findAllByPersonId(existing.getId())).thenReturn(List.of(fresh));
		// Her process closed yesterday. Not stubAcceptedRegistration: that would
		// re-stub the CURP as free and undo the whole point of this test.
		when(programAdmissionConfigQueryPort.findById(CLOSED_CONFIG_ID)).thenReturn(Optional
				.of(configIn(CLOSED_CONFIG_ID, WINDOW_OPEN, Instant.parse("2026-09-24T18:00:00Z"), MAX_CANDIDATES)));
		stubAcceptedRegistrationStubs(config(WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES));

		assertThatCode(() -> useCase.register(command())).doesNotThrowAnyException();
	}

	private static Candidate candidateRegisteredAt(Instant registeredAt) {
		return candidateRegisteredIn(ADMISSION_CONFIG_ID, registeredAt);
	}

	private static Candidate candidateRegisteredIn(UUID configId, Instant registeredAt) {
		Candidate candidate = new Candidate(UUID.randomUUID(), configId, "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "registeredAt", registeredAt);
		return candidate;
	}

	private static RegisterCandidateCommand command() {
		return new RegisterCandidateCommand(datosGenerales(), new Domicilio("Calle", "1", null, "Colonia", "Ciudad",
				"91000", UUID.randomUUID(), UUID.randomUUID()), new Contacto("a@b.com", null, "555"),
				new InformacionComplementaria(false, null, false, null, false, null, false, null, false, false, false,
						false, false),
				new Ingresos(BigDecimal.ZERO, false, null, null, null, null, null, null, null),
				new AntecedentesEscolares("Prep", SCHOOL_TYPE_ID, true, UUID.randomUUID(), UUID.randomUUID(), null,
						null, BigDecimal.valueOf(8.5), null, false),
				new SeleccionCarrera(ADMISSION_CONFIG_ID, CHANNEL_ID, true), true);
	}

	private static DatosGenerales datosGenerales() {
		return new DatosGenerales("CURP0000000000000000", "Juan", "Perez", "Lopez", LocalDate.of(2006, 1, 1),
				Gender.M, "Mexicana", null, null, null, null, MaritalStatus.SOLTERO, "Español", false);
	}
}
