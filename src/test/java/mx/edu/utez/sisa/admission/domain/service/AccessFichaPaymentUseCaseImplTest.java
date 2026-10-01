package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase.PaymentAccess;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccessFichaPaymentUseCaseImpl} — the "vuelve a pagar mi
 * ficha" access by folio + last-3 CURP characters.
 *
 * <p>The security-relevant assertions are {@code rejects*} / {@code throwsTheSame*Message*}: a wrong
 * folio and a wrong CURP must be indistinguishable to the caller, or the
 * public endpoint becomes a folio-existence oracle.
 */
@ExtendWith(MockitoExtension.class)
class AccessFichaPaymentUseCaseImplTest {

	private static final String FOLIO = "ADM-2026-000101";

	/** Real CURP shape: the last 3 characters are letter + 2 digits, NOT numeric. */
	private static final String CURP = "TOMA050312MDFRRN08";

	private static final String SUFFIX = "N08";

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID PERSON_ID = UUID.randomUUID();

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();

	// Sales window / quota ride along on the projection. This use case only reads
	// the config to name the program on the payment page, so the values just have
	// to be plausible — the rules themselves are covered in
	// RegisterCandidateUseCaseImplTest.
	private static final Instant WINDOW_OPEN = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant WINDOW_CLOSE = Instant.parse("2026-12-31T23:59:59Z");
	private static final int MAX_CANDIDATES = 40;

	/**
	 * The registration window's closing day, as stored on the ticket. Fixed dates
	 * rather than {@code LocalDate.now()} throughout, and deliberately different
	 * from each other: these tests are about <em>which</em> of the two windows a
	 * field reports, and if both dates happened to be the same day a mix-up
	 * between them would pass unnoticed.
	 */
	private static final LocalDate REGISTRATION_DEADLINE = LocalDate.of(2026, 9, 30);

	/** The tuition concept's {@code available_until}: the date that governs payment. */
	private static final LocalDate PAYMENT_CLOSES_ON = LocalDate.of(2026, 10, 5);

	/** The ficha's own plazo: registered 10/09 + 10 days → 20/09, before the sales window. */
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final Instant REGISTERED_AT = LocalDate.of(2026, 9, 10).atTime(12, 0).atZone(ZONE).toInstant();

	private static final int DEADLINE_DAYS = 10;

	/** The earlier of {@link #REGISTRATION_DEADLINE} and {@link #REGISTERED_AT} + 10. */
	private static final LocalDate PAYMENT_DEADLINE = LocalDate.of(2026, 9, 20);

	private static final Clock CLOCK = Clock.fixed(REGISTERED_AT, ZONE);

	private static final UUID PROGRAM_ID = UUID.randomUUID();

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

	private AccessFichaPaymentUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new AccessFichaPaymentUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, fichaAmountResolver, CLOCK,
				DEADLINE_DAYS);
		// Default: the concept closes its window on this date. Every test that
		// asserts on paymentClosesOn relies on it, and the ones that don't care
		// are unaffected because a non-stubbed mock would answer null anyway.
		lenient().when(fichaAmountResolver.paymentClosesOn(any())).thenReturn(PAYMENT_CLOSES_ON);
	}

	private static Candidate candidate() {
		Candidate candidate = new Candidate(PERSON_ID, ADMISSION_CONFIG_ID, FOLIO, true, true, null);
		// Candidate#id is JPA-assigned (no public setter), so a hand-built
		// instance has a null id — same seam the other ficha tests use.
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
		// The ficha's visible plazo is derived from when it was registered, so it
		// is pinned to a fixed day the same way the clock is.
		ReflectionTestUtils.setField(candidate, "registeredAt", REGISTERED_AT);
		return candidate;
	}

	private static Person person() {
		Person person = new Person(CURP, "Ana", "Torres", "Ramos", null);
		return person;
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("500.00"),
				"REF-20260924-000101", REGISTRATION_DEADLINE);
	}

	private void givenPendingCandidate() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.of(configInfo()));
	}

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo configInfo() {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
				ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID, "Ing. en Tecnologías de la Información",
				ProgramModality.PRESENCIAL, "2026-1", WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES);
	}

	@Test
	void access_returnsOnlyPaymentDataForAFolioAndCurpMatch() {
		givenPendingCandidate();

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.candidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(access.folio()).isEqualTo(FOLIO);
		assertThat(access.nombre()).isEqualTo("Ana Torres Ramos");
		assertThat(access.programName()).isEqualTo("Ing. en Tecnologías de la Información");
		assertThat(access.amount()).isEqualByComparingTo("500.00");
		assertThat(access.referenceNumber()).isEqualTo("REF-20260924-000101");
		assertThat(access.alreadyPaid()).isFalse();
		// a pending payment has no confirmation date and must not pretend to have one
		assertThat(access.paidAt()).isNull();
	}

	/**
	 * The two dates, and the reason they are separate fields.
	 *
	 * <p>{@code registrationDeadline} is a snapshot written at registration;
	 * {@code paymentClosesOn} is asked of the catalog on every access. That
	 * difference is the feature: extending a period is done in Conceptos de Pago
	 * and has to move the date on a ficha that was issued weeks earlier.
	 */
	@Test
	void access_reportsTheRegistrationDeadlineAndThePaymentWindowSeparately() {
		givenPendingCandidate();

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.registrationDeadline()).isEqualTo(REGISTRATION_DEADLINE);
		assertThat(access.paymentClosesOn()).isEqualTo(PAYMENT_CLOSES_ON);
		// The date promised to the applicant is the ficha's own plazo (earlier of
		// the sales window and registeredAt + N), never the concept's window.
		assertThat(access.paymentDeadline()).isEqualTo(PAYMENT_DEADLINE);
	}

	/**
	 * When the admission process closes before the ficha's own plazo, the closing
	 * date is the one the screen must promise.
	 *
	 * <p>Bounded by the config read live, not by the {@code registrationDeadline}
	 * frozen on the ticket when it was issued. That snapshot is exactly the value
	 * that goes stale: staff close a cohort by editing the config, and a promise
	 * derived from the snapshot keeps offering payment until the original date.
	 */
	@Test
	void theVisiblePaymentDeadlineIsTheEarlierOfTheProcessClosingAndTheFichaPlazo() {
		givenPendingCandidate();
		// Registered 10/09, own plazo to the 20th; her process closed on the 15th.
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(configClosingOn(LocalDate.of(2026, 9, 15))));

		assertThat(useCase.access(FOLIO, SUFFIX).paymentDeadline()).isEqualTo(LocalDate.of(2026, 9, 15));
	}

	/**
	 * A process that closes after the ficha's own window leaves her own plazo as
	 * the binding bound — the closing date must never hand anybody extra days.
	 */
	@Test
	void theVisiblePaymentDeadlineStaysOnTheFichaPlazoWhenTheProcessClosesLater() {
		givenPendingCandidate();
		// Own plazo ends the 20th; the process runs to December.
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(configClosingOn(LocalDate.of(2026, 12, 31))));

		assertThat(useCase.access(FOLIO, SUFFIX).paymentDeadline()).isEqualTo(PAYMENT_DEADLINE);
	}

	/**
	 * The flag the screen hides "Pagar" on, and the reason it is not the same as
	 * {@code candidateStatus}.
	 *
	 * <p>A ficha whose window closed but which the 00:10 sweep has not reached yet is
	 * still {@code REGISTERED} in the database. The portal must not offer a payment
	 * the checkout will refuse, so the flag is computed live from the window while
	 * the status keeps saying what the last sweep wrote down. Reading the status
	 * here instead would put a ten-minute hole in every ficha's deadline, every
	 * night.
	 */
	@Test
	void access_flagsAnExpiredFichaEvenBeforeTheSweepWritesTheStatus() {
		givenPendingCandidate();
		// Move the clock past the deadline; the candidate row is untouched and still
		// REGISTERED, exactly as it would be at 00:05.
		useCase = new AccessFichaPaymentUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, fichaAmountResolver,
				Clock.fixed(PAYMENT_DEADLINE.plusDays(1).atStartOfDay(ZONE).toInstant(), ZONE), DEADLINE_DAYS);

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.paymentExpired()).isTrue();
		assertThat(access.candidateStatus()).isEqualTo(CandidateStatus.REGISTERED);
	}

	/**
	 * A closed admission process ends the ficha even with days left on its own
	 * plazo, which is what this flag reports — the same rule the nightly sweep and
	 * the checkout gate apply.
	 */
	@Test
	void access_flagsAFichaWhoseAdmissionProcessClosedFirst() {
		givenPendingCandidate();
		// Registered 10/09, so her own plazo runs to the 20th; the process closed on
		// the 15th, before that.
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(configClosingOn(LocalDate.of(2026, 9, 15))));
		useCase = new AccessFichaPaymentUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, fichaAmountResolver,
				Clock.fixed(LocalDate.of(2026, 9, 16).atStartOfDay(ZONE).toInstant(), ZONE), DEADLINE_DAYS);

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.paymentExpired()).isTrue();
		assertThat(access.paymentDeadline()).isEqualTo(LocalDate.of(2026, 9, 15));
	}

	/** A ficha inside its window is not expired, whatever the status says. */
	@Test
	void access_doesNotFlagAFichaStillInsideItsWindow() {
		givenPendingCandidate();

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.paymentExpired()).isFalse();
		assertThat(access.candidateStatus()).isEqualTo(CandidateStatus.REGISTERED);
	}

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo configClosingOn(LocalDate closesOn) {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
				ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID, "Ing. en Tecnologías de la Información",
				ProgramModality.PRESENCIAL, "2026-1", WINDOW_OPEN,
				closesOn.atStartOfDay(ZONE).plusHours(23).toInstant(), MAX_CANDIDATES);
	}

	/** Read live, not snapshotted: the catalog decides, on every request. */
	@Test
	void thePaymentWindowIsReadLiveSoExtendingTheConceptMovesAPendingFicha() {
		givenPendingCandidate();
		when(fichaAmountResolver.paymentClosesOn(PROGRAM_ID)).thenReturn(LocalDate.of(2026, 11, 20));

		assertThat(useCase.access(FOLIO, SUFFIX).paymentClosesOn()).isEqualTo(LocalDate.of(2026, 11, 20));
	}

	/**
	 * No closing date configured is an absence, not a date. The screen has to be
	 * able to omit the row, and it can only do that if null survives.
	 */
	@Test
	void aConceptWithoutAClosingDateReportsNoPaymentWindow() {
		givenPendingCandidate();
		when(fichaAmountResolver.paymentClosesOn(PROGRAM_ID)).thenReturn(null);

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.paymentClosesOn()).isNull();
		assertThat(access.registrationDeadline()).isEqualTo(REGISTRATION_DEADLINE);
	}

	/**
	 * Access is the recovery path — folio plus three CURP characters — so it must
	 * not become unavailable because a lookup came back empty. The applicant
	 * already proved who she is; refusing to render would strand her with no way
	 * to see what she owes.
	 */
	@Test
	void aMissingAdmissionConfigStillRendersThePayment() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.empty());

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.amount()).isEqualByComparingTo("500.00");
		assertThat(access.programName()).isNull();
		assertThat(access.registrationDeadline()).isEqualTo(REGISTRATION_DEADLINE);
		assertThat(access.paymentClosesOn()).isNull();
		verify(fichaAmountResolver, never()).paymentClosesOn(any());
	}

	@Test
	void access_normalizesFolioCaseAndSurroundingWhitespace() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.of(configInfo()));

		PaymentAccess access = useCase.access("  adm-2026-000101  ", " n08 ");

		assertThat(access.folio()).isEqualTo(FOLIO);
	}

	@Test
	void access_reportsAlreadyPaidSoTheScreenShowsTheReceipt() {
		givenPendingCandidate();
		AdmissionPayment paid = payment();
		paid.markPaid("REC-20260924-000001");
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paid));

		PaymentAccess access = useCase.access(FOLIO, SUFFIX);

		assertThat(access.alreadyPaid()).isTrue();
		assertThat(access.receiptNumber()).isEqualTo("REC-20260924-000001");
		// the business asked to show a confirmation date next to the receipt
		assertThat(access.paidAt()).isNotNull();
	}

	@Test
	void access_rejectsUnknownFolio() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.access(FOLIO, SUFFIX))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("No encontramos una ficha de admisión con ese folio y CURP");

		verify(candidatePersonRepository, never()).findById(PERSON_ID);
	}

	@Test
	void access_rejectsWrongCurpSuffix() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));

		assertThatThrownBy(() -> useCase.access(FOLIO, "ZZZ"))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("No encontramos una ficha de admisión con ese folio y CURP");

		verify(admissionPaymentRepository, never()).findByCandidateId(CANDIDATE_ID);
	}

	/**
	 * The anti-enumeration guarantee: both rejections must produce the exact
	 * same message, or a caller can probe which folios exist.
	 */
	@Test
	void access_wrongFolioAndWrongCurpAreIndistinguishable() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.empty());
		Throwable unknownFolio = catchThrowable(() -> useCase.access(FOLIO, SUFFIX));

		setUp();
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		Throwable wrongCurp = catchThrowable(() -> useCase.access(FOLIO, "ZZZ"));

		assertThat(unknownFolio).isInstanceOf(CandidateNotFoundException.class);
		assertThat(wrongCurp).isInstanceOf(CandidateNotFoundException.class);
		assertThat(unknownFolio.getMessage()).isEqualTo(wrongCurp.getMessage());
	}

	@Test
	void access_rejectsBlankOrShortSuffix() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));

		assertThatThrownBy(() -> useCase.access(FOLIO, "")).isInstanceOf(CandidateNotFoundException.class);
		assertThatThrownBy(() -> useCase.access(FOLIO, "N0")).isInstanceOf(CandidateNotFoundException.class);
		assertThatThrownBy(() -> useCase.access(FOLIO, "N080")).isInstanceOf(CandidateNotFoundException.class);
		assertThatThrownBy(() -> useCase.access(FOLIO, null)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void access_rejectsWhenTheStoredPersonHasNoCurp() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		Person sinCurp = new Person(null, "Ana", "Torres", "Ramos", null);
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(sinCurp));

		assertThatThrownBy(() -> useCase.access(FOLIO, SUFFIX)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void access_rejectsWhenThePersonRowIsMissing() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.access(FOLIO, SUFFIX)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void access_rejectsWhenTheCandidateHasNoPaymentRow() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.access(FOLIO, SUFFIX)).isInstanceOf(CandidateNotFoundException.class);
	}

	private static Throwable catchThrowable(Runnable runnable) {
		try {
			runnable.run();
			return null;
		} catch (Throwable thrown) {
			return thrown;
		}
	}
}
