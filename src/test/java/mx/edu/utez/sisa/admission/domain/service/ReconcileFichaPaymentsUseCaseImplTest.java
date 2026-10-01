package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReconcileFichaPaymentsUseCaseImpl}: the nightly pass that asks
 * the bank about every attempt nobody settled.
 *
 * <p>Most of these cases are about what the sweep must <em>not</em> do. Every one of them
 * is an irreversible mistake — a place handed to somebody else, or a captured payment
 * forgotten — and the failure modes are quiet ones, so they are pinned explicitly instead
 * of being assumed away by the happy path.
 */
@ExtendWith(MockitoExtension.class)
class ReconcileFichaPaymentsUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private static final BigDecimal QUOTED = new BigDecimal("500.00");

	private static final Instant OPENED = Instant.parse("2026-09-30T12:00:00Z");

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	@Mock
	private ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	private ReconcileFichaPaymentsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReconcileFichaPaymentsUseCaseImpl(checkoutAttemptRepository, admissionPaymentRepository,
				evoPaymentsGateway, checkoutSlotClaimer, confirmFichaPaymentVerifiedUseCase);
	}

	private static CheckoutAttempt openAttempt(String orderId, UUID candidateId) {
		return new CheckoutAttempt(orderId, candidateId, QUOTED, OPENED);
	}

	private static AdmissionPayment pendingPayment() {
		AdmissionPayment payment = new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA,
				QUOTED, "REF-2026-000001", LocalDate.now().plusDays(10));
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		return payment;
	}

	private static AdmissionPayment paidPayment() {
		AdmissionPayment payment = pendingPayment();
		payment.markPaid("REC-20260930-000001");
		return payment;
	}

	/** {@code SUCCESS} with the captured amount, as the three-arg shape reports it. */
	private EvoPaymentsGatewayPort.EvoOrderStatus captured() {
		return new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", QUOTED);
	}

	private void givenOpenAttempts(CheckoutAttempt... attempts) {
		when(checkoutAttemptRepository.findOpenAttempts()).thenReturn(List.of(attempts));
	}

	private void givenPayment(AdmissionPayment payment) {
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
	}

	/**
	 * The branch the whole mechanism exists for: the applicant stopped looking, the money
	 * arrived afterwards, and the ficha must still become {@code PAID}.
	 */
	@Test
	void aCaptureFoundAtNightMarksTheFichaPaid() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(captured());

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
		// Delegated, not reimplemented: the sweep must not be a second way of marking a
		// payment paid, or the day the two disagree a capture yields a paid ficha with no
		// receipt.
		verify(confirmFichaPaymentVerifiedUseCase).confirm(CANDIDATE_ID, ORDER_ID);
		verify(checkoutSlotClaimer, never()).release(any());
	}

	@Test
	void aDefinitiveRefusalHandsThePlaceBackAndClosesTheRow() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isEqualTo(1);
		verify(checkoutSlotClaimer).release(CANDIDATE_ID);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/**
	 * An order the bank could not rule on is left alone, and left <em>open</em>.
	 *
	 * <p>Closing it would be the one way to lose money: the row is how the system knows to
	 * ask again tomorrow, and a closed row that actually captured is invisible forever.
	 */
	@Test
	void anInconclusiveAnswerWritesNothingAndLeavesTheAttemptOpen() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "PENDING", QUOTED, null));

		var result = useCase.reconcile();

		assertThat(result.heldAttempts()).isEqualTo(1);
		assertThat(result.closedRows()).isZero();
		verify(checkoutSlotClaimer, never()).release(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/**
	 * The failure this job exists to not perform.
	 *
	 * <p>"We could not ask" and "the bank refused" have to stay distinguishable, or a bank
	 * outage silently hands away every place whose payment is still in flight — sold twice,
	 * with one applicant paid and the other told the quota is full.
	 */
	@Test
	void anUnreachableBankReleasesNothing() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		// No payment stub: the lookup throws before the sweep ever reads the ficha, which
		// is exactly why an outage can be turned into no writes at all.
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenThrow(new EvoPaymentGatewayException("EVO no respondió"));

		var result = useCase.reconcile();

		assertThat(result.failedAttempts()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
		verify(checkoutSlotClaimer, never()).release(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	/**
	 * The real trace, made structural: the applicant abandoned attempt 1 and paid with
	 * attempt 2, so the ficha's column holds attempt 2 while attempt 1 is still open.
	 *
	 * <p>Before the {@code CAPTURED} close existed, attempt 1 stayed open for good, the
	 * sweep asked the bank about it every night forever, and every one of those nights it
	 * also asked the claimer to release a slot belonging to an already-paid ficha — which
	 * throws.
	 */
	@Test
	void anAbandonedAttemptOnAPaidFichaIsClosedWithoutTouchingTheQuota() {
		String abandoned = "TESTUTEZ-ADM-2026-000001-ABANDONED";
		givenOpenAttempts(openAttempt(abandoned, CANDIDATE_ID));
		givenPayment(paidPayment());
		when(evoPaymentsGateway.retrieveOrder(abandoned)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(abandoned, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isZero();
		assertThat(result.closedRows()).isEqualTo(1);
		verify(checkoutSlotClaimer, never()).release(any());
		// REJECTED, because that is what the bank said about THIS order. Recording CAPTURED
		// here would put a lie on the row to make the counts prettier.
		verify(checkoutSlotClaimer).closeAttempt(abandoned, CheckoutAttemptCloseReason.REJECTED);
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	@Test
	void anAttemptWhoseFichaWasJustPaidIsClosedAsCaptured() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(paidPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(captured());

		var result = useCase.reconcile();

		assertThat(result.closedRows()).isEqualTo(1);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.CAPTURED);
		verify(checkoutSlotClaimer, never()).release(any());
		// The browser got there in the seconds between the query and this write.
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/**
	 * One bad attempt must not abandon the rest. A single unexpected exception in a loop
	 * over every open payment in the system would otherwise freeze reconciliation until
	 * somebody noticed, which is the same as not having it.
	 */
	@Test
	void onePoisonedAttemptDoesNotAbandonTheOthers() {
		String first = "TESTUTEZ-ADM-2026-000001-A";
		String second = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(first, CANDIDATE_ID), openAttempt(second, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(first))
				.thenThrow(new ProgramAdmissionConfigCapacityReachedException("El cupo de esta carrera se agotó."));
		when(evoPaymentsGateway.retrieveOrder(second)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(second, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.failedAttempts()).isEqualTo(1);
		assertThat(result.releasedSlots()).isEqualTo(1);
		verify(checkoutSlotClaimer).closeAttempt(second, CheckoutAttemptCloseReason.REJECTED);
	}

	/**
	 * A row that closed between the query and the loop is not re-decided. The browser and
	 * the sweep reach the same attempt, and the first answer is the one that was true —
	 * {@code CheckoutAttempt#close} refuses the overwrite, so the sweep has to respect it
	 * rather than queue a second decision behind it.
	 */
	@Test
	void anAttemptClosedByTheBrowserMidRunIsLeftAlone() {
		CheckoutAttempt settled = openAttempt(ORDER_ID, CANDIDATE_ID);
		settled.close(CheckoutAttemptCloseReason.CAPTURED, OPENED.plusSeconds(30));
		givenOpenAttempts(settled);

		var result = useCase.reconcile();

		assertThat(result.closedRows()).isZero();
		assertThat(result.settledCaptures()).isZero();
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
	}

	@Test
	void aQuietNightReportsNothingChanged() {
		givenOpenAttempts();

		var result = useCase.reconcile();

		assertThat(result.changedAnything()).isFalse();
		assertThat(result.failedAttempts()).isZero();
	}

	@Test
	void aCaptureIsDecidedEvenWhenTheVerdictSaysFailure() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(
				ORDER_ID, "FAILURE", QUOTED, null, QUOTED, null, null, null, null, null));

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
		verify(checkoutSlotClaimer, never()).release(any());
	}

	@Test
	void anErrorNodeWithoutMoneyAlsoReleasesThePlace() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(
				ORDER_ID, "PENDING", null, null, BigDecimal.ZERO, null, null, null, null, "Pago rechazado"));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isEqualTo(1);
		verify(checkoutSlotClaimer).release(CANDIDATE_ID);
	}

	@Test
	void aZeroCapturedAmountIsNotACapture() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", QUOTED, null, BigDecimal.ZERO, null,
						null, null, null, null));

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isZero();
		assertThat(result.heldAttempts()).isEqualTo(1);
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	@Test
	void aMissingPaymentRowDoesNotStopTheRun() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		var result = useCase.reconcile();

		// isPaid() reads an absent row as "not paid", so the refusal path runs and the
		// claimer is the component that reports the missing ficha — as its javadoc says it
		// should, instead of the sweep silently pretending the ficha exists. Here the
		// claimer is a mock, so what this pins is that the run completed rather than
		// aborting: one unreconcilable attempt does not stop the other thirty-nine.
		assertThat(result.failedAttempts()).isZero();
		assertThat(result.releasedSlots()).isEqualTo(1);
		verify(checkoutSlotClaimer).release(CANDIDATE_ID);
	}

	/**
	 * The same row, but this time the claimer refuses — which is the real behaviour with a
	 * missing ficha, since {@code requirePendingPayment} throws rather than skipping. The
	 * sweep has to survive that refusal instead of abandoning the remaining attempts.
	 */
	@Test
	void anAttemptWhoseClaimCannotBeReleasedIsCountedNotFatal() {
		String broken = "TESTUTEZ-ADM-2026-000001-A";
		String fine = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(broken, CANDIDATE_ID), openAttempt(fine, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(broken))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(broken, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(fine))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(fine, "FAILURE", null));
		org.mockito.Mockito.doThrow(new IllegalStateException(
				"El pago " + CANDIDATE_ID + " no está PENDING, no se puede modificar la reserva de cupo"))
				.when(checkoutSlotClaimer).release(CANDIDATE_ID);

		var result = useCase.reconcile();

		assertThat(result.failedAttempts()).isEqualTo(2);
		assertThat(result.releasedSlots()).isZero();
	}
}