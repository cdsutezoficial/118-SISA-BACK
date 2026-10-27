package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseOutcome;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReleaseFichaPaymentSlotUseCaseImpl}, the endpoint the portal
 * calls when the applicant's browser gave up on the hosted checkout.
 *
 * <p>Since 2026-10-23 the place is held by the open checkout attempt itself, so
 * releasing it means closing the attempts the bank refused. What is pinned here is
 * mostly a set of refusals: a timeout is compatible with an order that was created
 * and captured seconds later, so only a definitive refusal of every open attempt of
 * the ficha frees anything.
 */
@ExtendWith(MockitoExtension.class)
class ReleaseFichaPaymentSlotUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID CHARGE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001-abc";

	/** A retry leaves the previous attempt behind, still open. */
	private static final String OLDER_ORDER_ID = "TESTUTEZ-ADM-2026-000001-xyz";

	private static final Instant OPENED = Instant.parse("2026-09-25T10:00:00Z");

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	private ReleaseFichaPaymentSlotUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReleaseFichaPaymentSlotUseCaseImpl(fichaPaymentPort, evoPaymentsGateway, checkoutSlotClaimer,
				checkoutAttemptRepository);
	}

	private void givenOnlyOpenAttempt() {
		givenOpenAttempts(ORDER_ID);
	}

	private void givenOpenAttempts(String... orderIds) {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID))
				.thenReturn(Arrays.stream(orderIds).map(this::openAttempt).toList());
	}

	private CheckoutAttempt openAttempt(String orderId) {
		return FichaTestData.attempt(orderId, CHARGE_ID, CANDIDATE_ID, new BigDecimal("550.00"), OPENED);
	}

	/** The browser's order is hers, and her ficha is in the given state. */
	private void givenFicha(FichaPaymentPort.FichaCharge charge) {
		lenient().when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(openAttempt(ORDER_ID)));
		lenient().when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(charge));
	}

	private void givenPendingFicha() {
		givenFicha(FichaTestData.pendingCharge(CHARGE_ID));
	}

	// ── the one case that frees anything ──

	/**
	 * The only row in §6 that releases: the bank refused and no money moved. The
	 * attempt is closed as {@code REJECTED} — which is what gives the place back,
	 * since only an open attempt holds one.
	 */
	@Test
	void aDefiniteBankRefusalClosesTheAttemptAndSoFreesThePlace() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		assertThat(result.slotReleased()).isTrue();
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
		// Once, not twice: the order the browser named is already among the open attempts.
		verify(evoPaymentsGateway, times(1)).retrieveOrder(ORDER_ID);
	}

	/** An explicit error node is the bank's own account of a refusal. */
	@Test
	void anErrorNodeFromTheBankAlsoReleasesTheSlot() {
		givenPendingFicha();
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				null, new BigDecimal("550.00"), null, BigDecimal.ZERO, null, null, null, null, "CARD_DECLINED"));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
	}

	// ── the sibling picture that made a per-order decision unsafe ──

	/**
	 * The browser reports the timeout of the newest order while an older attempt of the
	 * same ficha — the one holding her money — is still open. The older order is asked
	 * too, and its capture keeps everything open.
	 */
	@Test
	void aRefusedRetryDoesNotFreeAPlaceAnOlderSiblingCaptured() {
		givenPendingFicha();
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "SUCCESS", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** A sibling still working is the same answer for the same reason: nothing is idle. */
	@Test
	void aRefusedOrderKeepsItsPlaceWhileASiblingIsStillInFlight() {
		givenPendingFicha();
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "PENDING", null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/**
	 * Two refusals mean the ficha is genuinely idle: both attempts are closed, and only
	 * those — an attempt opened after the bank was asked is not in the fold and keeps
	 * holding its place.
	 */
	@Test
	void twoRefusedAttemptsAreBothClosedAndNothingElse() {
		givenPendingFicha();
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(OLDER_ORDER_ID, "FAILURE", null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.SLOT_RELEASED);
		assertThat(result.slotReleased()).isTrue();
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
		verify(checkoutSlotClaimer).closeAttempt(OLDER_ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
		verify(checkoutSlotClaimer, times(2)).closeAttempt(any(), eq(CheckoutAttemptCloseReason.REJECTED));
	}

	/** A sibling we cannot ask about is not a refusal: nothing is written. */
	@Test
	void anUnreachableBankOnASiblingFreesNothing() {
		givenPendingFicha();
		givenOpenAttempts(ORDER_ID, OLDER_ORDER_ID);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(OLDER_ORDER_ID)).thenThrow(new EvoPaymentGatewayException(
				"No se pudo contactar al proveedor de pagos (EVO): timeout"));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	// ── the cases that must never free anything ──

	/** The order is still {@code PENDING} and may be captured in the next second. */
	@Test
	void aStillPendingOrderKeepsItsSlotEvenThoughTheBrowserGaveUp() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "PENDING", null, BigDecimal.ZERO));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_IN_PROGRESS);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** The money arrived; the daily sweep turns this into a paid ficha. */
	@Test
	void aCapturedOrderKeepsItsSlotAndIsLeftForTheSweepToSettle() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00")));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** {@code SUCCESS} with nothing captured cannot be diagnosed, so it is retained. */
	@Test
	void successWithoutACaptureIsRetainedRatherThanReleased() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("550.00"), null,
						BigDecimal.ZERO, null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.RETAINED_UNEXPLAINED);
		assertThat(result.slotReleased()).isFalse();
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** A {@code FAILURE} that captured something is not a refusal: capture outranks it. */
	@Test
	void aCaptureOutranksAFailureVerdict() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID,
				"FAILURE", new BigDecimal("550.00"), null, new BigDecimal("550.00"), null, null, null, null, null));

		var result = useCase.release(CANDIDATE_ID, ORDER_ID);

		assertThat(result.outcome()).isEqualTo(ReleaseOutcome.PAYMENT_CAPTURED);
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	// ── what this endpoint refuses before asking anybody ──

	/** The only abuse this public endpoint could suffer: releasing somebody else's place. */
	@Test
	void anOrderIdFromAnotherFichaIsRejectedBeforeTheGatewayIsConsulted() {
		String someoneElses = "TESTUTEZ-ADM-2026-000009-xyz";
		when(checkoutAttemptRepository.findByOrderId(someoneElses)).thenReturn(Optional.of(
				FichaTestData.attempt(someoneElses, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("550.00"))));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, someoneElses))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");

		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	@Test
	void anUnknownOrderIdIsRejected() {
		when(checkoutAttemptRepository.findByOrderId("NOPE")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, "NOPE"))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");
	}

	/** Nothing to settle without an id, and "the latest attempt" would be a guess. */
	@Test
	void aMissingOrderIdIsRejected() {
		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, " "))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("obligatorio");

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	/** A paid ficha holds its place permanently, so there is nothing to give back. */
	@Test
	void anAlreadyPaidFichaIs409RatherThanASilentNoOp() {
		givenFicha(FichaTestData.paidCharge(CHARGE_ID));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateAlreadyPaidException.class);

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	@Test
	void anAttemptWhoseChargeIsGoneIs404() {
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(openAttempt(ORDER_ID)));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	/** A gateway outage must change nothing at all. */
	@Test
	void aGatewayOutageLeavesEverythingUntouched() {
		givenPendingFicha();
		givenOnlyOpenAttempt();
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenThrow(new EvoPaymentGatewayException(
				"No se pudo contactar al proveedor de pagos (EVO): timeout"));

		assertThatThrownBy(() -> useCase.release(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}
}
