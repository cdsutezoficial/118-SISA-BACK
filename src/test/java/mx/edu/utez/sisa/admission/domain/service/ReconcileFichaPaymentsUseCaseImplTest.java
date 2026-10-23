package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

	/** A second candidate, for the cases where isolation is what is under test. */
	private static final UUID OTHER_CANDIDATE_ID = UUID.randomUUID();

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	private static final UUID CHARGE_ID = UUID.randomUUID();

	private static final UUID OTHER_CHARGE_ID = UUID.randomUUID();

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	@Mock
	private ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	private ReconcileFichaPaymentsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReconcileFichaPaymentsUseCaseImpl(checkoutAttemptRepository, fichaPaymentPort,
				evoPaymentsGateway, checkoutSlotClaimer, confirmFichaPaymentVerifiedUseCase);
	}

	private static UUID chargeOf(UUID candidateId) {
		return candidateId.equals(CANDIDATE_ID) ? CHARGE_ID : OTHER_CHARGE_ID;
	}

	private static CheckoutAttempt openAttempt(String orderId, UUID candidateId) {
		return FichaTestData.attempt(orderId, chargeOf(candidateId), candidateId, QUOTED, OPENED);
	}

	/** A ficha mid-checkout: its open attempts are what hold the place. */
	private static FichaPaymentPort.FichaCharge pendingPayment() {
		return FichaTestData.pendingCharge(CHARGE_ID);
	}

	private static FichaPaymentPort.FichaCharge claimedPayment() {
		return pendingPayment();
	}

	private static FichaPaymentPort.FichaCharge claimedPaymentFor(UUID candidateId) {
		return FichaTestData.pendingCharge(chargeOf(candidateId));
	}

	private static FichaPaymentPort.FichaCharge paidPayment() {
		return FichaTestData.paidCharge(CHARGE_ID);
	}

	/** {@code SUCCESS} with the captured amount, as the three-arg shape reports it. */
	private EvoPaymentsGatewayPort.EvoOrderStatus captured() {
		return new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", QUOTED);
	}

	private void givenOpenAttempts(CheckoutAttempt... attempts) {
		when(checkoutAttemptRepository.findOpenAttempts()).thenReturn(List.of(attempts));
	}

	private void givenPayment(FichaPaymentPort.FichaCharge payment) {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(payment));
	}

	private void givenPaymentFor(UUID candidateId, FichaPaymentPort.FichaCharge payment) {
		when(fichaPaymentPort.findCharge(chargeOf(candidateId))).thenReturn(Optional.of(payment));
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
	}

	@Test
	void aDefinitiveRefusalHandsThePlaceBackAndClosesTheRow() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isEqualTo(1);
		// The claim it read travels with the decision, so a claim stamped since then is not
		// the one this release is allowed to touch.
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
		// The browser got there in the seconds between the query and this write.
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/**
	 * One bad attempt must not abandon the rest. A single unexpected exception in a loop
	 * over every open payment in the system would otherwise freeze reconciliation until
	 * somebody noticed, which is the same as not having it.
	 *
	 * <p>Two <em>different</em> fichas, because a poisoned attempt no longer fails alone:
	 * it fails the whole ficha it belongs to, since one unreadable order is exactly the one
	 * that would have said whether her money was already there. The same-candidate case is
	 * pinned separately below.
	 */
	@Test
	void onePoisonedFichaDoesNotAbandonTheOthers() {
		String first = "TESTUTEZ-ADM-2026-000001-A";
		String second = "TESTUTEZ-ADM-2026-000002-B";
		givenOpenAttempts(openAttempt(first, CANDIDATE_ID), openAttempt(second, OTHER_CANDIDATE_ID));
		// No payment row for the poisoned ficha is stubbed on purpose: the group fails
		// while asking the bank, before any ficha is read, and a stub left behind would be
		// a promise the code never asked for.
		givenPaymentFor(OTHER_CANDIDATE_ID, claimedPaymentFor(OTHER_CANDIDATE_ID));
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
	 * And the same ficha is the harder case: a sibling attempt that could not be asked
	 * about keeps the whole group undecided.
	 *
	 * <p>The refused attempt in here is the tempting one. Read on its own it is a clean
	 * {@code FAILURE} with nothing captured, and releasing it was this sweep's job every
	 * night for weeks. But the sibling is the answer nobody has, so the place stays: a
	 * career one place short for a day, against a capture that lands a second later.
	 */
	@Test
	void anUnreadableSiblingKeepsThePlaceEvenThoughTheOtherAttemptWasRefused() {
		String unreadable = "TESTUTEZ-ADM-2026-000001-A";
		String refused = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(refused, CANDIDATE_ID), openAttempt(unreadable, CANDIDATE_ID));
		// No ficha is stubbed: the group fails while it is still asking the bank, before any
		// payment row is read, and this case is about that moment.
		when(evoPaymentsGateway.retrieveOrder(unreadable)).thenThrow(new EvoPaymentGatewayException("EVO no respondió"));
		when(evoPaymentsGateway.retrieveOrder(refused)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(refused, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isZero();
		assertThat(result.settledCaptures()).isZero();
		assertThat(result.closedRows()).isZero();
		// Two, not one: the group is the unit of decision, so an unreadable order fails
		// both of the rows it prevented anyone from settling. The refused one is counted as
		// failed because it was never acted on, not because the bank misbehaved on it.
		assertThat(result.failedAttempts()).isEqualTo(2);
		assertThat(result.heldAttempts()).isEqualTo(2);
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
	}

	/**
	 * The defect, exactly.
	 *
	 * <p>Two attempts on one ficha: an old one the bank refused, and the current one whose
	 * money arrived. Asked in query order and written as it went, the refusal gave the
	 * place back and the capture then marked her paid — a sold place handed to somebody
	 * else and a paid applicant left without one. The fold sees a capture and keeps her.
	 */
	@Test
	void aRefusedAttemptDoesNotFreeThePlaceWhenASiblingCaptured() {
		String refused = "TESTUTEZ-ADM-2026-000001-A";
		String paid = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(refused, CANDIDATE_ID), openAttempt(paid, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(refused)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(refused, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(paid))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(paid, "SUCCESS", QUOTED));

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
		// Marked paid once, through the browser's own interactor: this sweep must not be a
		// second way of marking a payment paid, or the day the two disagree a capture
		// yields a paid ficha with no receipt.
		verify(confirmFichaPaymentVerifiedUseCase).confirm(CANDIDATE_ID, paid);
		// The refused row records what the bank said about it. Recording CAPTURED
		// everywhere would put a lie on the history to make the counts prettier.
		verify(checkoutSlotClaimer).closeAttempt(refused, CheckoutAttemptCloseReason.REJECTED);
	}

	/** The same pair the other way round, because query order is an accident and never a fact. */
	@Test
	void theCaptureIsFoundNoMatterWhichAttemptTheQueryReturnsFirst() {
		String paid = "TESTUTEZ-ADM-2026-000001-B";
		String refused = "TESTUTEZ-ADM-2026-000001-A";
		givenOpenAttempts(openAttempt(paid, CANDIDATE_ID), openAttempt(refused, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(paid))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(paid, "SUCCESS", QUOTED));
		when(evoPaymentsGateway.retrieveOrder(refused)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(refused, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
	}

	/**
	 * The place goes back once, not once per attempt.
	 *
	 * <p>Both orders were refused, so nothing is in flight, and the count is a place rather
	 * than a row: two {@code release} calls would clear the same claim twice and put two
	 * "lugar liberado" in tonight's log for one place that existed.
	 */
	@Test
	void twoRefusedAttemptsReleaseExactlyOnePlace() {
		String first = "TESTUTEZ-ADM-2026-000001-A";
		String second = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(first, CANDIDATE_ID), openAttempt(second, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(first)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(first, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(second)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(second, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isEqualTo(1);
		assertThat(result.closedRows()).isEqualTo(2);
		verify(checkoutSlotClaimer).closeAttempt(first, CheckoutAttemptCloseReason.REJECTED);
		verify(checkoutSlotClaimer).closeAttempt(second, CheckoutAttemptCloseReason.REJECTED);
	}

	/**
	 * The browser won the race in the seconds between the query and the write.
	 *
	 * <p>That is a settled payment, not a failed run: the ficha is paid either way and the
	 * only thing left is this sweep's bookkeeping. Logged as an error it would train the
	 * office to ignore the line that actually means something.
	 */
	@Test
	void aConfirmationTheBrowserAlreadyWonIsNotAFailure() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(pendingPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(captured());
		when(confirmFichaPaymentVerifiedUseCase.confirm(CANDIDATE_ID, ORDER_ID))
				.thenThrow(new CandidateAlreadyPaidException("La ficha ya estaba pagada."));

		var result = useCase.reconcile();

		assertThat(result.failedAttempts()).isZero();
		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
	}

	/**
	 * The capturing row closed under us, and the sibling is only refused.
	 *
	 * <p>Nothing here may mark her paid through a row the browser already settled, and
	 * nothing may free the place either — a {@code FAILURE} sibling plus a capture we are
	 * not allowed to act on is not a release. The next run asks again.
	 */
	@Test
	void aCaptureOnAClosedRowIsNeitherConfirmedNorReleased() {
		String settled = "TESTUTEZ-ADM-2026-000001-A";
		String refused = "TESTUTEZ-ADM-2026-000001-B";
		CheckoutAttempt closed = openAttempt(settled, CANDIDATE_ID);
		closed.close(CheckoutAttemptCloseReason.CAPTURED, OPENED.plusSeconds(30));
		givenOpenAttempts(closed, openAttempt(refused, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(settled))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(settled, "SUCCESS", QUOTED));
		when(evoPaymentsGateway.retrieveOrder(refused)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(refused, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isZero();
		assertThat(result.settledCaptures()).isZero();
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), anyString());
	}

	/** A capture is still settled when a sibling order cannot be ruled on either way. */
	@Test
	void aCaptureIsSettledEvenWhenASiblingIsUnanswered() {
		String paid = "TESTUTEZ-ADM-2026-000001-A";
		String unanswered = "TESTUTEZ-ADM-2026-000001-B";
		givenOpenAttempts(openAttempt(paid, CANDIDATE_ID), openAttempt(unanswered, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(paid))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(paid, "SUCCESS", QUOTED));
		when(evoPaymentsGateway.retrieveOrder(unanswered))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(unanswered, "PENDING", QUOTED, null));

		var result = useCase.reconcile();

		assertThat(result.settledCaptures()).isEqualTo(1);
		assertThat(result.releasedSlots()).isZero();
		verify(confirmFichaPaymentVerifiedUseCase).confirm(CANDIDATE_ID, paid);
		// The unanswered row stays open: tomorrow's run reads it under a paid ficha, where
		// closing it needs no further question.
		verify(checkoutSlotClaimer, never()).closeAttempt(eq(unanswered), any());
	}

	/**
	 * A refused sibling on an already-paid ficha still closes, and her place is never
	 * touched.
	 *
	 * <p>The capture that paid her need not be among tonight's open rows — it closed days
	 * ago — so the fold can see nothing but refusals. Believing them would strip a paid
	 * ficha of the place she paid for.
	 */
	@Test
	void anAlreadyPaidFichaIsNeverStrippedOfHerPlaceByRefusedSiblings() {
		String refused = "TESTUTEZ-ADM-2026-000001-A";
		givenOpenAttempts(openAttempt(refused, CANDIDATE_ID));
		givenPayment(paidPayment());
		when(evoPaymentsGateway.retrieveOrder(refused)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(refused, "FAILURE", null));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isZero();
		assertThat(result.settledCaptures()).isZero();
		verify(checkoutSlotClaimer).closeAttempt(refused, CheckoutAttemptCloseReason.REJECTED);
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
		verify(checkoutSlotClaimer, never()).closeAttempt(anyString(), any());
		// Asked once, written never. The read is deliberate: this row says a capture landed
		// and the ficha still is not paid, which is exactly the state that must not be
		// resolved by forgetting the row. What must not happen is a second close on top of
		// the one the browser already recorded.
		verify(evoPaymentsGateway, times(1)).retrieveOrder(ORDER_ID);
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
	}

	@Test
	void anErrorNodeWithoutMoneyAlsoReleasesThePlace() {
		givenOpenAttempts(openAttempt(ORDER_ID, CANDIDATE_ID));
		givenPayment(claimedPayment());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(
				ORDER_ID, "PENDING", null, null, BigDecimal.ZERO, null, null, null, null, "Pago rechazado"));

		var result = useCase.reconcile();

		assertThat(result.releasedSlots()).isEqualTo(1);
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
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.empty());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		var result = useCase.reconcile();

				// A charge that cannot be read is not a paid one: the refused attempt is closed
		// on what the bank said, which is what frees the place, and the run goes on.
		assertThat(result.failedAttempts()).isZero();
		assertThat(result.releasedSlots()).isEqualTo(1);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);
	}

	/**
	 	 * A write that fails for one ficha (closing its attempt) is counted, not fatal.
	 */
	@Test
	void aFichaWhoseClaimCannotBeReleasedIsCountedNotFatal() {
		String broken = "TESTUTEZ-ADM-2026-000001-A";
		String fine = "TESTUTEZ-ADM-2026-000002-B";
		givenOpenAttempts(openAttempt(broken, CANDIDATE_ID), openAttempt(fine, OTHER_CANDIDATE_ID));
		givenPayment(claimedPayment());
		givenPaymentFor(OTHER_CANDIDATE_ID, claimedPaymentFor(OTHER_CANDIDATE_ID));
		when(evoPaymentsGateway.retrieveOrder(broken))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(broken, "FAILURE", null));
		when(evoPaymentsGateway.retrieveOrder(fine))
				.thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(fine, "FAILURE", null));
				Mockito.doThrow(new IllegalStateException("No se pudo cerrar el intento " + broken))
				.when(checkoutSlotClaimer).closeAttempt(eq(broken), any());

		var result = useCase.reconcile();

		// One ficha failed, not two attempts: the failure is a property of the ficha, and
		// its row stays open for tomorrow rather than being counted as settled.
		assertThat(result.failedAttempts()).isEqualTo(1);
		assertThat(result.releasedSlots()).isEqualTo(1);
		assertThat(result.heldAttempts()).isEqualTo(1);
	}
}