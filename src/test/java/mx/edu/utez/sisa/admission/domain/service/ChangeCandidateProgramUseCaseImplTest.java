package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeCandidateProgramUseCase.Mode;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeCandidateProgramUseCase.ProgramChangePreview;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateProgramChangeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateProgramChangeNotAllowedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cambio de carrera desde la ficha: qué camino toma según el pago y cuándo no
 * se permite.
 */
@ExtendWith(MockitoExtension.class)
class ChangeCandidateProgramUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T18:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	private static final UUID CANDIDATE_ID = UUID.randomUUID();
	private static final UUID OLD_CONFIG = UUID.randomUUID();
	private static final UUID NEW_CONFIG = UUID.randomUUID();
	private static final UUID NEW_PROGRAM = UUID.randomUUID();
	private static final UUID FICHA_PAYMENT = UUID.randomUUID();
	private static final UUID NEW_PAYMENT = UUID.randomUUID();
	private static final UUID ACTOR = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private CandidateProgramChangeRepository programChangeRepository;

	private ChangeCandidateProgramUseCaseImpl useCase;

	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new ChangeCandidateProgramUseCaseImpl(candidateRepository, programAdmissionConfigQueryPort,
				fichaAmountResolver, fichaPaymentPort, admissionQuotaPort, checkoutAttemptRepository,
				programChangeRepository, CLOCK, 10, 15);
		candidate = new Candidate(UUID.randomUUID(), OLD_CONFIG, "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
		ReflectionTestUtils.setField(candidate, "registeredAt", Instant.parse("2026-10-25T18:00:00Z"));
		candidate.linkFichaPayment(FICHA_PAYMENT);

		lenient().when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		lenient().when(programAdmissionConfigQueryPort.findById(OLD_CONFIG)).thenReturn(Optional.of(config(OLD_CONFIG,
				UUID.randomUUID(), "Ingeniería", 40)));
		lenient().when(programAdmissionConfigQueryPort.findById(NEW_CONFIG))
				.thenReturn(Optional.of(config(NEW_CONFIG, NEW_PROGRAM, "Licenciatura", 40)));
		lenient().when(fichaAmountResolver.resolveConcept(eq(NEW_PROGRAM), any())).thenReturn(
				new PaymentConceptQueryPort.FichaConcept(UUID.randomUUID(), "FICHA_LIC", "Ficha", null, null));
		lenient().when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of());
	}

	@Test
	void unpaidMovesTheSameChargeAndKeepsTheCandidateRegistered() {
		charge(false);
		when(fichaPaymentPort.quote(FICHA_PAYMENT)).thenReturn(quote("1120.00", "0.00"))
				.thenReturn(quote("1650.00", "0.00"));

		useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR);

		verify(fichaPaymentPort).reassignProgram(FICHA_PAYMENT, "FICHA_LIC", NEW_PROGRAM);
		verify(fichaPaymentPort, never()).rebill(any());
		verifyNoInteractions(admissionQuotaPort);
		assertThat(candidate.getAdmissionConfigId()).isEqualTo(NEW_CONFIG);
		assertThat(candidate.getFichaPaymentId()).isEqualTo(FICHA_PAYMENT);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
		ArgumentCaptor<CandidateProgramChange> audit = ArgumentCaptor.forClass(CandidateProgramChange.class);
		verify(programChangeRepository).save(audit.capture());
		assertThat(audit.getValue().isWasPaid()).isFalse();
		assertThat(audit.getValue().getChangedBy()).isEqualTo(ACTOR);
	}

	@Test
	void paidAndMoreExpensiveLeavesTheDifferenceWhileKeepingThePaidFicha() {
		paidCandidate();
		quotaFree();
		when(fichaPaymentPort.quote(FICHA_PAYMENT)).thenReturn(quote("1420.00", "0.00"));
		when(fichaPaymentPort.rebill(any())).thenReturn(new FichaPaymentPort.ProgramChangeAmounts(NEW_PAYMENT,
				new BigDecimal("1750.00"), new BigDecimal("1420.00"), BigDecimal.ZERO, new BigDecimal("330.00"),
				BigDecimal.ZERO));

		useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR);

		ArgumentCaptor<FichaPaymentPort.RebillFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.RebillFichaCommand.class);
		verify(fichaPaymentPort).rebill(command.capture());
		assertThat(command.getValue().fromPaymentId()).isEqualTo(FICHA_PAYMENT);
		assertThat(command.getValue().reference()).isEqualTo("REF-ADM-2026-000001-C1");
		// Mismo plazo de la ficha (10 días) contado desde el cambio.
		assertThat(command.getValue().dueAt().atZone(CLOCK.getZone()).toLocalDate())
				.isEqualTo(LocalDate.of(2026, 11, 7));
		assertThat(candidate.getFichaPaymentId()).isEqualTo(FICHA_PAYMENT);
		assertThat(candidate.getProgramChangePaymentId()).isEqualTo(NEW_PAYMENT);
		assertThat(candidate.collectingPaymentId()).isEqualTo(NEW_PAYMENT);
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(candidate.getAdmissionConfigId()).isEqualTo(NEW_CONFIG);
	}

	@Test
	void paidAndCoveredMakesTheNewChargeTheFicha() {
		paidCandidate();
		quotaFree();
		when(fichaPaymentPort.quote(FICHA_PAYMENT)).thenReturn(quote("1800.00", "0.00"));
		when(fichaPaymentPort.rebill(any())).thenReturn(new FichaPaymentPort.ProgramChangeAmounts(NEW_PAYMENT,
				new BigDecimal("1400.00"), new BigDecimal("1800.00"), BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal("400.00")));

		useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR);

		assertThat(candidate.getFichaPaymentId()).isEqualTo(NEW_PAYMENT);
		assertThat(candidate.getProgramChangePaymentId()).isNull();
	}

	@Test
	void aPendingDifferenceIsWhatTheNextChangeStartsFrom() {
		paidCandidate();
		candidate.awaitProgramChangePayment(NEW_PAYMENT);
		quotaFree();
		UUID latest = UUID.randomUUID();
		when(fichaPaymentPort.quote(NEW_PAYMENT)).thenReturn(quote("1750.00", "0.00"));
		when(programChangeRepository.countByCandidateId(CANDIDATE_ID)).thenReturn(1L);
		when(fichaPaymentPort.rebill(any())).thenReturn(new FichaPaymentPort.ProgramChangeAmounts(latest,
				new BigDecimal("1650.00"), new BigDecimal("1420.00"), BigDecimal.ZERO, new BigDecimal("230.00"),
				BigDecimal.ZERO));

		useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR);

		ArgumentCaptor<FichaPaymentPort.RebillFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.RebillFichaCommand.class);
		verify(fichaPaymentPort).rebill(command.capture());
		assertThat(command.getValue().fromPaymentId()).isEqualTo(NEW_PAYMENT);
		assertThat(command.getValue().reference()).isEqualTo("REF-ADM-2026-000001-C2");
		assertThat(candidate.getProgramChangePaymentId()).isEqualTo(latest);
	}

	@Test
	void paidRefusesACareerWithNoPlaceLeft() {
		paidCandidate();
		when(admissionQuotaPort.lockQuota(NEW_CONFIG)).thenReturn(new AdmissionQuotaPort.QuotaState(40));
		when(admissionQuotaPort.countOccupiedExcluding(eq(NEW_CONFIG), eq(CANDIDATE_ID), any())).thenReturn(40L);

		assertThatThrownBy(() -> useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);
		verify(fichaPaymentPort, never()).rebill(any());
		assertThat(candidate.getAdmissionConfigId()).isEqualTo(OLD_CONFIG);
	}

	@Test
	void enrolledCandidateCannotChange() {
		ReflectionTestUtils.setField(candidate, "status", CandidateStatus.ENROLLED);

		assertThatThrownBy(() -> useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR))
				.isInstanceOf(CandidateProgramChangeNotAllowedException.class);
		assertThat(useCase.isAllowed(CANDIDATE_ID)).isFalse();
	}

	@Test
	void expiredFichaCannotChange() {
		ReflectionTestUtils.setField(candidate, "status", CandidateStatus.PAYMENT_EXPIRED);
		charge(false);

		assertThatThrownBy(() -> useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR))
				.isInstanceOf(CandidateProgramChangeNotAllowedException.class);
	}

	@Test
	void anOpenCheckoutBlocksTheChange() {
		charge(false);
		openAttemptCreatedAt(CLOCK.instant().minusSeconds(60));

		assertThatThrownBy(() -> useCase.change(CANDIDATE_ID, NEW_CONFIG, ACTOR))
				.isInstanceOf(CandidateProgramChangeNotAllowedException.class);
		verify(fichaPaymentPort, never()).reassignProgram(any(), any(), any());
		assertThat(useCase.blockedReason(CANDIDATE_ID)).hasValueSatisfying(r -> assertThat(r).contains("en curso"));
	}

	@Test
	void anAttemptLeftOpenPastTheHoldDoesNotBlock() {
		// Se cerró el panel de EVO sin pagar: el intento se queda STARTED.
		charge(false);
		openAttemptCreatedAt(CLOCK.instant().minusSeconds(16 * 60));

		assertThat(useCase.isAllowed(CANDIDATE_ID)).isTrue();
		assertThat(useCase.blockedReason(CANDIDATE_ID)).isEmpty();
	}

	@Test
	void anOpenAttemptDoesNotBlockAPaidFichaWithNothingPending() {
		charge(true);
		openAttemptCreatedAt(CLOCK.instant().minusSeconds(60));

		assertThat(useCase.isAllowed(CANDIDATE_ID)).isTrue();
	}

	@Test
	void anOpenAttemptBlocksWhileADifferenceIsPending() {
		charge(true);
		candidate.awaitProgramChangePayment(UUID.randomUUID());
		openAttemptCreatedAt(CLOCK.instant().minusSeconds(60));

		assertThat(useCase.isAllowed(CANDIDATE_ID)).isFalse();
	}

	private void openAttemptCreatedAt(Instant createdAt) {
		CheckoutAttempt attempt = mock(CheckoutAttempt.class);
		lenient().when(attempt.getCreatedAt()).thenReturn(createdAt);
		lenient().when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(attempt));
	}

	@Test
	void theSameCareerIsANoOp() {
		useCase.change(CANDIDATE_ID, OLD_CONFIG, ACTOR);

		verifyNoInteractions(fichaPaymentPort, programChangeRepository);
	}

	@Test
	void unpaidPreviewWarnsWhenTheNewCareerWindowAlreadyClosed() {
		charge(false);
		when(programAdmissionConfigQueryPort.findById(NEW_CONFIG)).thenReturn(Optional.of(new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(
				NEW_CONFIG, ProgramAdmissionConfigStatus.OPEN, NEW_PROGRAM, "Licenciatura", null, null, null,
				Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-20T05:00:00Z"), 40)));
		when(fichaPaymentPort.quote(FICHA_PAYMENT)).thenReturn(quote("1120.00", "0.00"));
		when(fichaPaymentPort.quoteForProgram(FICHA_PAYMENT, "FICHA_LIC", NEW_PROGRAM))
				.thenReturn(quote("1650.00", "825.00"));

		ProgramChangePreview preview = useCase.preview(CANDIDATE_ID, NEW_CONFIG);

		assertThat(preview.mode()).isEqualTo(Mode.UNPAID);
		assertThat(preview.newAmount()).isEqualByComparingTo("825.00");
		assertThat(preview.toPay()).isEqualByComparingTo("825.00");
		assertThat(preview.paymentWindowClosed()).isTrue();
	}

	@Test
	void paidPreviewReportsTheDifferenceAndTheQuota() {
		paidCandidate();
		when(fichaPaymentPort.quote(FICHA_PAYMENT)).thenReturn(quote("1420.00", "0.00"));
		when(fichaPaymentPort.previewRebill(FICHA_PAYMENT, "FICHA_LIC", NEW_PROGRAM)).thenReturn(
				new FichaPaymentPort.ProgramChangeAmounts(null, new BigDecimal("1750.00"), new BigDecimal("1420.00"),
						BigDecimal.ZERO, new BigDecimal("330.00"), BigDecimal.ZERO));
		when(admissionQuotaPort.countOccupiedExcluding(eq(NEW_CONFIG), eq(CANDIDATE_ID), any())).thenReturn(40L);

		ProgramChangePreview preview = useCase.preview(CANDIDATE_ID, NEW_CONFIG);

		assertThat(preview.mode()).isEqualTo(Mode.PAID);
		assertThat(preview.previousAmount()).isEqualByComparingTo("1420.00");
		assertThat(preview.toPay()).isEqualByComparingTo("330.00");
		assertThat(preview.quotaAvailable()).isFalse();
		assertThat(preview.newPaymentDeadline()).isEqualTo(LocalDate.of(2026, 11, 7));
	}

	private void paidCandidate() {
		candidate.markPaid();
		charge(true);
	}

	private void quotaFree() {
		when(admissionQuotaPort.lockQuota(NEW_CONFIG)).thenReturn(new AdmissionQuotaPort.QuotaState(40));
		when(admissionQuotaPort.countOccupiedExcluding(eq(NEW_CONFIG), eq(CANDIDATE_ID), any())).thenReturn(3L);
	}

	private void charge(boolean paid) {
		lenient().when(fichaPaymentPort.findCharge(FICHA_PAYMENT)).thenReturn(Optional.of(
				new FichaPaymentPort.FichaCharge(FICHA_PAYMENT, "REF-ADM-2026-000001",
						paid ? AdmissionPaymentStatus.PAID : AdmissionPaymentStatus.PENDING, false, null,
						Instant.parse("2026-10-25T18:00:00Z"), null, null)));
	}

	private static FichaPaymentPort.FichaQuote quote(String original, String discount) {
		BigDecimal originalAmount = new BigDecimal(original);
		BigDecimal discountAmount = new BigDecimal(discount);
		return new FichaPaymentPort.FichaQuote(UUID.randomUUID(), "FICHA", "Ficha", UUID.randomUUID(),
				originalAmount, discountAmount, originalAmount.subtract(discountAmount), null, false, true, null);
	}

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config(UUID id, UUID programId, String name,
			int max) {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(id, ProgramAdmissionConfigStatus.OPEN,
				programId, name, null, null, null, Instant.parse("2026-09-01T00:00:00Z"),
				Instant.parse("2026-12-15T05:00:00Z"), max);
	}
}
