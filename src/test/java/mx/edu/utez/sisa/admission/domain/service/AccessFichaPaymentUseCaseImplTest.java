package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private AccessFichaPaymentUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new AccessFichaPaymentUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort);
	}

	private static Candidate candidate() {
		Candidate candidate = new Candidate(PERSON_ID, ADMISSION_CONFIG_ID, FOLIO, true, true, null);
		// Candidate#id is JPA-assigned (no public setter), so a hand-built
		// instance has a null id — same seam the other ficha tests use.
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
		return candidate;
	}

	private static Person person() {
		Person person = new Person(CURP, "Ana", "Torres", "Ramos", null);
		return person;
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("500.00"),
				"REF-20260924-000101", LocalDate.now().plusDays(10));
	}

	private void givenPendingCandidate() {
		when(candidateRepository.findByFolio(FOLIO)).thenReturn(Optional.of(candidate()));
		when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.of(configInfo()));
	}

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo configInfo() {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
				ProgramAdmissionConfigStatus.OPEN, UUID.randomUUID(), "Ing. en Tecnologías de la Información",
				ProgramModality.PRESENCIAL, "2026-1");
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
