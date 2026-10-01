package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseOutcome;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
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

	/**
	 * A retry leaves the previous attempt behind: {@code orderId} moves to the newest one
	 * and only that one is still named by the ficha's row.
	 */
	private static final String OLDER_ORDER_ID = "TESTUTEZ-ADM-2026-000001-xyz";

	private static final Instant OPENED = Instant.parse("2026-09-25T10:00:00Z");

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	private ReleaseFichaPaymentSlotUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReleaseFichaPaymentSlotUseCaseImpl(admissionPaymentRepository, evoPaymentsGateway,
				checkoutSlotClaimer, checkoutAttemptRepository);
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

	/**
	 * The claim as it stands, which is what the release carries into the claimer.
	 *
	 * <p>Every case that frees a place needs a claimed ficha, because a release against an
	 * unclaimed one is refused — "there was nothing to give back" is a different answer
	 * from "the place is now free", and the endpoint must be able to tell them apart.
	 *
	 * <p>Memoised, because claiming stamps the current instant: building it twice would
	 * hand a verify a different claim than the stub answered with, and the comparison it
	 * pins would silently stop being tested.
	 */
	private AdmissionPayment claimedWithOrder() {
		if (claimed == null) {
			claimed = pendingWithOrder();
			claimed.claimCheckoutSlot();
		}
		return claimed;
	}

	/** Memoised by {@link #claimedWithOrder()}; see why there. */
	private AdmissionPayment claimed;

	/**
	 * The applicant's single open attempt, which is what the endpoint finds when no retry
	 * happened behind her.
	 *
	 * <p>Stubbed in {@code setUp} because every pre-existing case here is about one order's
	 * verdict; the sibling cases below override it to mount the two-attempt picture that
	 * made a per-order decision unsafe.
	 */
	private void givenOnlyOpenAttempt() {
		givenOpenAttempts(ORDER_ID);
	}

	private void givenOpenAttempts(String... orderIds) {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID))
				.thenReturn(Arrays.stream(orderIds).map(this::openAttempt).toList());
	}

	private CheckoutAttempt openAttempt(String orderId) {
		return new CheckoutAttempt(orderId, CANDIDATE_ID, new BigDecimal("550.00"), OPENED);
	}

	private void givenPayment(AdmissionPayment payment) {
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
	}

	/** The claimer as it behaves for real: it reports whether that is what freed the place. */
	private void givenReleaseSucceeds() {
		when(checkoutSlotClaimer.release(any(), any())).thenReturn(true);
	}

	// ── the one case that frees anything ──

	/**
	 * The only row in §6 that releases: the bank refused and no money moved. The slot
	 * goes back and the attempt is closed as {@code REJECTED} — an order did exist and
	 * the bank said no, which is the fact the history should keep.
	 */
	@Test
	void aDefiniteBankRefusalHandsTheSlotBackAndClosesTheAttempt() {
		givenPayment(claimedWithOrder());
		givenOnlyOpenAttempt();
		givenReleaseSucceeds();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		assertThat(result.slotReleased()).isTrue();
		verify(checkoutSlotClaimer).release(CANDIDATE_ID, claimedWithOrder().getCheckoutClaimedAt());
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
		// Once, not twice: the order the browser named is already among the open attempts,
		// and asking it again would double the cost of every give-up report.
		verify(evoPaymentsGateway, times(1)).retrieveOrder(ORDER_ID);
	}

	/**
	 * An explicit error node releases even when {@code result} is not literally
	 * {@code FAILURE}: the error is the bank's own account of what happened, and a
	 * verdict that hides it would keep a slot held over nothing.
	 */
	@Test
	void anErrorNodeFromTheBankAlsoReleasesTheSlot() {
		givenPayment(claimedWithOrder());
		givenReleaseSucceeds();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				null, new BigDecimal("550.00"), null, BigDecimal.ZERO, null, null, null, null, "CARD_DECLINED"));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		verify(checkoutSlotClaimer).release(CANDIDATE_ID, claimedWithOrder().getCheckoutClaimedAt());
	}

	// ── the sibling picture that made a per-order decision unsafe ──

	/**
	 * The bug this endpoint had. {@code AdmissionPayment#orderId} is overwritten on every
	 * retry, so the browser reports the timeout of the newest order while an older attempt
	 * of the same ficha — the one holding her money — is still open at the bank. Refusing
	 * the newest one and handing the place back oversold a career she had already paid for.
	 *
	 * <p>The refusal is therefore not enough on its own: the older order is asked too.
	 */
	@Test
	void aRefusedRetryDoesNotFreeAPlaceAnOlderSiblingCaptured() {
		givenPayment(claimedWithOrder());
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "SUCCESS", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any(), any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** A sibling still working is the same answer for the same reason: nothing is idle. */
	@Test
	void aRefusedOrderKeepsItsPlaceWhileASiblingIsStillInFlight() {
		givenPayment(claimedWithOrder());
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "PENDING", null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		// Her own order is refused, and it still cannot be called a release: a sibling of
		// hers is mid-checkout, and that is what is holding the place. Told the same thing
		// as a live order, because from the quota's point of view that is what she has.
		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any(), any());
	}

	/** Two refusals mean the ficha is genuinely idle, so both paths open and the place goes back. */
	@Test
	void twoRefusedAttemptsLeaveNothingInFlightAndFreeThePlace() {
		givenPayment(claimedWithOrder());
		givenReleaseSucceeds();
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "FAILURE", null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		assertThat(result.slotReleased()).isTrue();
		verify(checkoutSlotClaimer).release(CANDIDATE_ID, claimedWithOrder().getCheckoutClaimedAt());
	}

	/**
	 * A sibling we cannot ask about is not a refusal.
	 *
	 * <p>Failing to ask propagates, so nothing is written and the sweep retries — which
	 * is the whole point: unreachable is the one answer that must never look like "no".
	 */
	@Test
	void anUnreachableBankOnASiblingFreesNothing() {
		givenPayment(claimedWithOrder());
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID)).thenThrow(new EvoPaymentGatewayException(
				"No se pudo contactar al proveedor de pagos (EVO): timeout"));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).release(any(), any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/**
	 * The claim this call read is not the claim that is current, so nothing is freed.
	 *
	 * <p>No transaction spans the gateway call here, so between reading the ficha's claim
	 * and releasing it the applicant can start another checkout. That newer checkout is
	 * holding the place, and its own order is now {@code payment.orderId} — so a release
	 * that ignored the comparison would hand away a live claim.
	 */
	@Test
	void aClaimStampedAfterTheDecisionKeepsThePlace() {
		givenPayment(claimedWithOrder());
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(checkoutSlotClaimer.release(any(), any())).thenReturn(false);

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
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
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "PENDING", null, BigDecimal.ZERO));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any(), any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/**
	 * The money arrived. The browser timeout was cosmetic. Releasing would hand out a
	 * place that was paid for; the daily sweep is what turns this into a paid ficha.
	 */
	@Test
	void aCapturedOrderKeepsItsSlotAndIsLeftForTheSweepToSettle() {
		givenPayment(pendingWithOrder());
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any(), any());
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
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00"), null,
						BigDecimal.ZERO, null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.RETAINED_UNEXPLAINED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).release(any(), any());
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
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				"FAILURE", new BigDecimal("550.00"), null, new BigDecimal("550.00"), null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		verify(checkoutSlotClaimer, never()).release(any(), any());
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
		verify(checkoutSlotClaimer, never()).release(any(), any());
	}

	/** Nothing to settle without an id, and "the latest attempt" would be a guess. */
	@Test
	void aMissingOrderIdIsRejected() {
		givenPayment(pendingWithOrder());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, " "))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("obligatorio");

		verify(checkoutSlotClaimer, never()).release(any(), any());
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

		verify(checkoutSlotClaimer, never()).release(any(), any());
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
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenThrow(new EvoPaymentGatewayException(
				"No se pudo contactar al proveedor de pagos (EVO): timeout"));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).release(any(), any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}
}
