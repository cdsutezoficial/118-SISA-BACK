package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentInProgressException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PendingCheckoutGuard}: before a second session is opened, every
 * open attempt of the ficha is resolved with the bank (E1, prevention).
 */
@ExtendWith(MockitoExtension.class)
class PendingCheckoutGuardTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID CHARGE_ID = UUID.randomUUID();

	private static final String OLD_ORDER = "TESTUTEZ-ADM-2026-000001-A";

	private static final BigDecimal AMOUNT = new BigDecimal("1750.00");

	/** Two hours after {@link #oldAttempt()} was opened: it is past the hold. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);

	private static final int HOLD_MINUTES = 60;

	private static final String YOUNG_ORDER = "TESTUTEZ-ADM-2026-000001-B";

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	@Mock
	private ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	private PendingCheckoutGuard guard;

	@BeforeEach
	void setUp() {
		guard = new PendingCheckoutGuard(checkoutAttemptRepository, evoPaymentsGateway, fichaPaymentPort,
				checkoutSlotClaimer, confirmFichaPaymentVerifiedUseCase, CLOCK, HOLD_MINUTES);
	}

	@Test
	void withNoOpenAttemptTheNewSessionGoesAhead() {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of());

		assertThatCode(() -> guard.requireNoPaymentInFlight(candidate())).doesNotThrowAnyException();
		verify(evoPaymentsGateway, never()).retrieveOrder(anyString());
	}

	@Test
	void aRefusedSessionIsClosedAndTheNewOneGoesAhead() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "FAILURE", null));

		assertThatCode(() -> guard.requireNoPaymentInFlight(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer).closeAttempt(OLD_ORDER, CheckoutAttemptCloseReason.REJECTED);
	}

	/** Money authorized and not yet captured could still be charged: no second session. */
	@Test
	void anAuthorizationNotYetCapturedBlocksTheNewOne() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "SUCCESS", AMOUNT, AMOUNT, null, null,
				null, null, null, null));

		assertThatThrownBy(() -> guard.requireNoPaymentInFlight(candidate()))
				.isInstanceOf(PaymentInProgressException.class);
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	/**
	 * A session opened and closed without a card reads as unresolved — no capture, no
	 * refusal. It must not lock the applicant out; it stays open for the reconciliation.
	 */
	@Test
	void anAbandonedSessionDoesNotBlockAndStaysOpen() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "PENDING", AMOUNT, null));

		assertThatCode(() -> guard.requireNoPaymentInFlight(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	/** The old session paid: it is applied now and no new session is opened. */
	@Test
	void aCaptureOfWhatIsBeingCollectedIsAppliedInsteadOfOpeningAnother() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "SUCCESS", AMOUNT));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));

		assertThatThrownBy(() -> guard.requireNoPaymentInFlight(candidate()))
				.isInstanceOf(CandidateAlreadyPaidException.class);
		verify(confirmFichaPaymentVerifiedUseCase).confirm(CANDIDATE_ID, OLD_ORDER);
	}

	/** A capture on a charge that is no longer owed is saldo a favor, and does not block. */
	@Test
	void aCaptureOnAChargeNoLongerOwedBecomesSaldoAFavor() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "SUCCESS", AMOUNT));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.cancelledCharge(CHARGE_ID)));

		assertThatCode(() -> guard.requireNoPaymentInFlight(candidate())).doesNotThrowAnyException();
		verify(fichaPaymentPort).registerOverpayment(CHARGE_ID, OLD_ORDER, AMOUNT);
		verify(checkoutSlotClaimer).closeAttempt(OLD_ORDER, CheckoutAttemptCloseReason.CAPTURED);
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/**
	 * An order the bank cannot be asked about neither blocks nor is closed: it stays open
	 * for the nightly reconciliation, which books it if it ever captures.
	 */
	@Test
	void anUnreadableSessionDoesNotBlockAndIsNotClosed() {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(oldAttempt()));
		when(evoPaymentsGateway.retrieveOrder(OLD_ORDER)).thenThrow(new EvoPaymentGatewayException("EVO no respondió"));

		assertThatCode(() -> guard.requireNoPaymentInFlight(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	// -- E5: before a career change --

	private static EvoPaymentsGatewayPort.EvoOrderStatus tookNothing(String orderId) {
		return new EvoPaymentsGatewayPort.EvoOrderStatus(orderId, "PENDING", AMOUNT, BigDecimal.ZERO, BigDecimal.ZERO,
				null, null, null, null, null);
	}

	private static CheckoutAttempt youngAttempt() {
		return FichaTestData.attempt(YOUNG_ORDER, CHARGE_ID, CANDIDATE_ID, AMOUNT, Instant.parse("2026-11-03T11:40:00Z"));
	}

	private void givenYoungAttempt(EvoPaymentsGatewayPort.EvoOrderStatus answer) {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(youngAttempt()));
		when(evoPaymentsGateway.retrieveOrder(YOUNG_ORDER)).thenReturn(answer);
	}

	@Test
	void beforeAChange_aRefusedSessionIsClosedHoweverOld() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "FAILURE", null));

		assertThatCode(() -> guard.settleBeforeProgramChange(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer).closeAttempt(OLD_ORDER, CheckoutAttemptCloseReason.REJECTED);
	}

	@Test
	void beforeAChange_anAbandonedSessionPastTheHoldIsClosedAsExpired() {
		givenOldAttempt(tookNothing(OLD_ORDER));

		assertThatCode(() -> guard.settleBeforeProgramChange(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer).closeAttempt(OLD_ORDER, CheckoutAttemptCloseReason.ORDER_EXPIRED);
	}

	/** The E5 example: the old session can still be paid, and would settle the moved charge. */
	@Test
	void beforeAChange_aSessionStillInsideTheHoldBlocks() {
		givenYoungAttempt(tookNothing(YOUNG_ORDER));

		assertThatThrownBy(() -> guard.settleBeforeProgramChange(candidate()))
				.isInstanceOf(PaymentInProgressException.class);
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	@Test
	void beforeAChange_anAuthorizationBlocksHoweverOld() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "SUCCESS", AMOUNT, AMOUNT, null, null,
				null, null, null, null));

		assertThatThrownBy(() -> guard.settleBeforeProgramChange(candidate()))
				.isInstanceOf(PaymentInProgressException.class);
	}

	@Test
	void beforeAChange_aCaptureIsAppliedFirstAndTheChangeGoesAhead() {
		givenOldAttempt(new EvoPaymentsGatewayPort.EvoOrderStatus(OLD_ORDER, "SUCCESS", AMOUNT));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));

		assertThatCode(() -> guard.settleBeforeProgramChange(candidate())).doesNotThrowAnyException();
		verify(confirmFichaPaymentVerifiedUseCase).confirm(CANDIDATE_ID, OLD_ORDER);
	}

	@Test
	void beforeAChange_anUnreadableSessionInsideTheHoldBlocks() {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(youngAttempt()));
		when(evoPaymentsGateway.retrieveOrder(YOUNG_ORDER)).thenThrow(new EvoPaymentGatewayException("EVO no respondió"));

		assertThatThrownBy(() -> guard.settleBeforeProgramChange(candidate()))
				.isInstanceOf(PaymentInProgressException.class);
	}

	@Test
	void beforeAChange_anUnreadableSessionPastTheHoldIsLeftOpenAndDoesNotBlock() {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(oldAttempt()));
		when(evoPaymentsGateway.retrieveOrder(OLD_ORDER)).thenThrow(new EvoPaymentGatewayException("EVO no respondió"));

		assertThatCode(() -> guard.settleBeforeProgramChange(candidate())).doesNotThrowAnyException();
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	private void givenOldAttempt(EvoPaymentsGatewayPort.EvoOrderStatus answer) {
		when(checkoutAttemptRepository.findOpenAttemptsByCandidateId(CANDIDATE_ID)).thenReturn(List.of(oldAttempt()));
		when(evoPaymentsGateway.retrieveOrder(OLD_ORDER)).thenReturn(answer);
	}

	private static CheckoutAttempt oldAttempt() {
		return FichaTestData.attempt(OLD_ORDER, CHARGE_ID, CANDIDATE_ID, AMOUNT,
				Instant.parse("2026-11-03T10:00:00Z"));
	}

	private static Candidate candidate() {
		Candidate candidate = new Candidate(UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
		candidate.linkFichaPayment(CHARGE_ID);
		return candidate;
	}
}
