package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseOutcome;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReleaseFichaPaymentSlotUseCaseImpl}, the endpoint the portal
 * calls when the applicant's browser gave up on the hosted checkout.
 *
 * <p>The class exists because those two browser callbacks — {@code onEvoTimeout} and
 * {@code onEvoError} — used to be a private matter of the front, and the quota slot
 * they were holding stayed held: the next applicant to press "Pagar" on that career
 * was refused for a place the first applicant had already walked away from.
 *
 * <p>What is pinned here is mostly a set of refusals. The tempting implementation is
 * to release the slot because the browser said so, and that would oversell: a
 * timeout is compatible with an order that was created and captured seconds later.
 * So this class is mostly about the four ways the gateway can answer and the single
 * one of them that is allowed to free anything.
 */
@ExtendWith(MockitoExtension.class)
class ReleaseFichaPaymentSlotUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001-abc";

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	private ReleaseFichaPaymentSlotUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReleaseFichaPaymentSlotUseCaseImpl(admissionPaymentRepository, evoPaymentsGateway,
				checkoutSlotClaimer);
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("550.00"),
				"REF-2026-000001", null);
	}

	/** A ficha mid-checkout: the order is persisted, which is what the browser echoes back. */
	private AdmissionPayment pendingWithOrder() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		return payment;
	}

	private void givenPayment(AdmissionPayment payment) {
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
	}

	// ── the one case that frees anything ──

	/**
	 * The only row in §6 that releases: the bank refused and no money moved. The slot
	 * goes back and the attempt is closed as {@code REJECTED} — an order did exist and
	 * the bank said no, which is the fact the history should keep.
	 */
	@Test
	void aDefiniteBankRefusalHandsTheSlotBackAndClosesTheAttempt() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		assertThat(result.slotReleased()).isTrue();
		verify(checkoutSlotClaimer).release(CANDIDATE_ID);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
	}

	/**
	 * An explicit error node releases even when {@code result} is not literally
	 * {@code FAILURE}: the error is the bank's own account of what happened, and a
	 * verdict that hides it would keep a slot held over nothing.
	 */
	@Test
	void anErrorNodeFromTheBankAlsoReleasesTheSlot() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				null, new BigDecimal("550.00"), null, BigDecimal.ZERO, null, null, null, null, "CARD_DECLINED"));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		verify(checkoutSlotClaimer).release(CANDIDATE_ID);
	}

	// ── the three cases that must never free anything ──

	/**
	 * The dangerous one, and the reason this endpoint asks the bank at all. The
	 * browser reported a timeout; the order is still {@code PENDING} and may be
	 * captured in the next second. Releasing here hands the last place of a career to
	 * somebody else while this applicant's money is on its way.
	 */
	@Test
	void aStillPendingOrderKeepsItsSlotEvenThoughTheBrowserGaveUp() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "PENDING", null, BigDecimal.ZERO));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/**
	 * The money arrived. The browser timeout was cosmetic. Releasing would hand out a
	 * place that was paid for; the daily sweep is what turns this into a paid ficha.
	 */
	@Test
	void aCapturedOrderKeepsItsSlotAndIsLeftForTheSweepToSettle() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any());
	}

	/**
	 * {@code SUCCESS} with nothing captured. §6 documents that this cannot be
	 * diagnosed — {@code Retrieve Order} returns no gateway code — so it is retained
	 * rather than released on a guess, and the expiry sweep is what eventually frees
	 * the place.
	 */
	@Test
	void successWithoutACaptureIsRetainedRatherThanReleased() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00"), null,
						BigDecimal.ZERO, null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.RETAINED_UNEXPLAINED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any());
	}

	/**
	 * A {@code FAILURE} that nonetheless captured something is not a refusal: money
	 * moved, so the slot stays and the ficha becomes paid. Capture outranks
	 * {@code result}, and reading the table the other way round would free a place
	 * that was bought.
	 */
	@Test
	void aCaptureOutranksAFailureVerdict() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				"FAILURE", new BigDecimal("550.00"), null, new BigDecimal("550.00"), null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		verify(checkoutSlotClaimer, never()).release(any());
	}

	// ── what this endpoint refuses before asking anybody ──

	/**
	 * The only abuse this publicly reachable endpoint could suffer: settling somebody
	 * else's claim. The order id has to be the one persisted for this ficha, which
	 * makes the applicant's browser the only thing that can release her own slot.
	 */
	@Test
	void anOrderIdFromAnotherFichaIsRejectedBeforeTheGatewayIsConsulted() {
		givenPayment(pendingWithOrder());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, "TESTUTEZ-ADM-2026-000009-xyz"))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");

		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(checkoutSlotClaimer, never()).release(any());
	}

	/** Nothing to settle without an id, and "the latest attempt" would be a guess. */
	@Test
	void aMissingOrderIdIsRejected() {
		givenPayment(pendingWithOrder());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, " "))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("obligatorio");

		verify(checkoutSlotClaimer, never()).release(any());
	}

	/**
	 * A paid ficha holds its place permanently, so there is nothing to give back. The
	 * browser can legitimately report a timeout for a payment that was confirmed
	 * moments earlier, and a quiet 200 would hide that path instead of closing it.
	 */
	@Test
	void anAlreadyPaidFichaIs409RatherThanASilentNoOp() {
		AdmissionPayment paid = pendingWithOrder();
		paid.markPaid("REC-20260930-000001");
		givenPayment(paid);

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateAlreadyPaidException.class);

		verify(checkoutSlotClaimer, never()).release(any());
	}

	@Test
	void aCandidateWithoutAPaymentRowIs404() {
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	/**
	 * A gateway outage must change nothing at all. If it released on the way out, an
	 * unreachable bank would be indistinguishable from a refusal — and the sweep
	 * exists precisely to retry what could not be answered now.
	 */
	@Test
	void aGatewayOutageLeavesEverythingUntouched() {
		givenPayment(pendingWithOrder());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenThrow(new EvoPaymentGatewayException(
				"No se pudo contactar al proveedor de pagos (EVO): timeout"));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).release(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}
}
