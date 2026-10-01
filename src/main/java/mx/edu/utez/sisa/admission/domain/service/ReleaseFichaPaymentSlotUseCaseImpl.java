package mx.edu.utez.sisa.admission.domain.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;

/**
 * {@link ReleaseFichaPaymentSlotUseCase}: the browser said it gave up, the bank gets
 * the deciding vote.
 *
 * <p>The whole class is one question asked in the right order — <em>may this money
 * still be taken?</em> — and the answer decides whether the quota slot goes back. It
 * is asked of the gateway rather than of the caller because {@code onEvoTimeout} and
 * {@code onEvoError} are reports about the browser, and both are compatible with an
 * order that was created and captured afterwards. Releasing on that word alone would
 * oversell a career: the applicant's money is captured at the bank while a second
 * applicant takes the place that was sold to them.
 *
 * <p>Which is also why this is not a smaller version of
 * {@code ConfirmFichaPaymentVerifiedUseCaseImpl}. That one refuses to mark anything
 * paid without proof; this one refuses to free anything without proof. Both ask
 * {@code Retrieve Order}, and both would rather leave a slot held for a day than
 * commit either irreversible mistake. A slot held too long costs a career one place
 * until the expiry sweep; a slot released too early costs it a real oversell.
 *
 * <p>Not {@code @Transactional}: like the checkout, this method calls a third party
 * and must not hold a transaction open across it. The read happens here, the write
 * happens in {@link CheckoutSlotClaimer} in its own short transaction, and only after
 * the gateway has answered.
 *
 * <p><b>The whole ficha, not the one order.</b> The applicant's own attempt is checked
 * first and the request only carries that order id, which used to be enough to decide:
 * one bank answer about one order. It is not. {@code AdmissionPayment#orderId} is
 * overwritten on every retry, so a ficha that started a checkout, went back and started
 * another has an older attempt still open — and it may be the one holding her money.
 * Releasing because the newest order was refused would hand away a place a captured
 * sibling had paid for, which is the oversell this endpoint exists to prevent, reached
 * through the retry path instead of the timeout path. So every open attempt of the ficha
 * is asked about, {@link OrderSettlementDecider#decideFicha} folds the answers, and the
 * slot goes back only when nothing is in flight at all.
 *
 * <p><b>And the release itself is a comparison.</b> Nothing here holds a transaction
 * across the gateway call, by design, so between reading the ficha's claim and writing
 * the release the applicant can start another checkout. The claim read before asking is
 * therefore the value handed to the claimer, and a claim stamped since then is not
 * released: her refusal was real, but the place now belongs to a live order.
 */
public class ReleaseFichaPaymentSlotUseCaseImpl implements ReleaseFichaPaymentSlotUseCase {

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	/**
	 * Needed to see the attempts the ficha's {@code orderId} column no longer names.
	 * Without it this class could only ever ask about one order, which is the whole bug.
	 */
	private final CheckoutAttemptRepository checkoutAttemptRepository;

	public ReleaseFichaPaymentSlotUseCaseImpl(AdmissionPaymentRepository admissionPaymentRepository,
			EvoPaymentsGatewayPort evoPaymentsGateway, CheckoutSlotClaimer checkoutSlotClaimer,
			CheckoutAttemptRepository checkoutAttemptRepository) {
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
	}

	@Override
	public ReleaseResult release(UUID candidateId, String orderId) {
		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		// A paid ficha holds its place permanently, so there is nothing to give back.
		// Reported rather than silently ignored: the browser can legitimately report a
		// timeout for a payment that was in fact confirmed moments earlier, and a
		// quiet 200 there would hide a duplicate-payment path.
		if (payment.getPaymentStatus() == AdmissionPaymentStatus.PAID) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidateId + " ya estaba pagada.");
		}

		if (orderId == null || orderId.isBlank()) {
			throw new InvalidPaymentVerificationException(
					"El identificador del pedido es obligatorio para liberar el lugar.");
		}

		// The applicant's own attempt, and only her own. Without this check the
		// endpoint would accept any order id and settle somebody else's claim, which
		// is the one abuse this publicly reachable endpoint could suffer.
		if (!orderId.equals(payment.getOrderId())) {
			throw new InvalidPaymentVerificationException(
					"El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo.");
		}

		// Every open attempt, not just the one the browser named, and all of them asked
		// before anything is written. The fold, not the single answer, is what frees the
		// place: a refusal we understand is not a licence while a sibling of the same ficha
		// may already have captured.
		Map<String, EvoPaymentsGatewayPort.EvoOrderStatus> statuses = askAboutEveryOpenAttempt(candidateId, orderId);
		EvoPaymentsGatewayPort.EvoOrderStatus status = statuses.get(orderId);
		OrderSettlementDecider.Verdict fichaVerdict = OrderSettlementDecider.decideFicha(verdictsOf(statuses));

		if (fichaVerdict != OrderSettlementDecider.Verdict.RELEASEABLE) {
			// Nothing is written. Every attempt stays open on purpose so the daily sweep
			// asks the bank again about all of them at once: closing one on "we don't know
			// yet" would be the one way to lose a capture that landed after this call.
			return new ReleaseResult(candidateId, orderId, outcomeWhenPlaceIsKept(fichaVerdict, status), false);
		}

		// The claim travels with the decision. Between reading it above and writing it
		// here the applicant may have started another checkout, which stamps a newer
		// claim; releasing then would hand away the place that live order is holding.
		// The answer is not an error — her attempt is genuinely refused — but the place
		// stays, so the sweep settles it once the newer attempt reaches a verdict.
		if (!checkoutSlotClaimer.release(candidateId, payment.getCheckoutClaimedAt())) {
			return new ReleaseResult(candidateId, orderId, ReleaseOutcome.PAYMENT_IN_PROGRESS, false);
		}

		// REJECTED, not SESSION_TIMEOUT/ERROR: those record what the browser saw, and
		// this row is being closed on what the bank said. An order did exist and was
		// refused — that is the fact worth keeping in the history. Siblings stay for the
		// sweep, which is where every row of a ficha is settled together.
		checkoutSlotClaimer.closeAttempt(orderId, CheckoutAttemptCloseReason.REJECTED);
		return new ReleaseResult(candidateId, orderId, ReleaseOutcome.SLOT_RELEASED, true);
	}

	/**
	 * The bank's answer for every open attempt of this ficha, keyed by order id.
	 *
	 * <p>One gateway call per order, all of them before the single decision — the same
	 * order of operations the sweep uses, and for the same reason. Asking one order and
	 * writing is what made a refusal actionable while a sibling capture was still a
	 * possibility; here it would be actionable while the money was already hers.
	 *
	 * <p>The order the browser named is asked here rather than separately above, so a
	 * ficha is never billed twice for the same question. If its row is not among the open
	 * ones — already closed, or never recorded — it is still asked, since it was verified
	 * against this ficha above and it is the order whose verdict the applicant is owed.
	 *
	 * <p>A failure to ask is not a refusal and is not caught: the exception travels to the
	 * 502 that leaves everything untouched, which is the honest answer for "we could not
	 * find out", and is what the sweep counts as failed rather than guessing at.
	 */
	private Map<String, EvoPaymentsGatewayPort.EvoOrderStatus> askAboutEveryOpenAttempt(UUID candidateId,
			String orderId) {
		Map<String, EvoPaymentsGatewayPort.EvoOrderStatus> statuses = new LinkedHashMap<>();
		for (CheckoutAttempt attempt : checkoutAttemptRepository.findOpenAttemptsByCandidateId(candidateId)) {
			statuses.put(attempt.getOrderId(), evoPaymentsGateway.retrieveOrder(attempt.getOrderId()));
		}
		statuses.computeIfAbsent(orderId, evoPaymentsGateway::retrieveOrder);
		return statuses;
	}

	private static List<OrderSettlementDecider.Verdict> verdictsOf(
			Map<String, EvoPaymentsGatewayPort.EvoOrderStatus> statuses) {
		return statuses.values().stream().map(OrderSettlementDecider::decide).toList();
	}

	/**
	 * What to say when the place was <em>not</em> freed.
	 *
	 * <p>A fold of {@link OrderSettlementDecider.Verdict#CAPTURED} is reported as a
	 * capture even when the order the browser named was the one refused: money moved on her
	 * ficha, and that is what the sweep will make of it. Reporting her own refusal instead
	 * would tell her to pay again for a place she has already paid for.
	 *
	 * <p>Everything else is projected from her own order by
	 * {@link #outcomeForOwnAttempt(EvoPaymentsGatewayPort.EvoOrderStatus)}.
	 */
	private static ReleaseOutcome outcomeWhenPlaceIsKept(OrderSettlementDecider.Verdict fichaVerdict,
			EvoPaymentsGatewayPort.EvoOrderStatus status) {
		return fichaVerdict == OrderSettlementDecider.Verdict.CAPTURED ? ReleaseOutcome.PAYMENT_CAPTURED
				: outcomeForOwnAttempt(status);
	}

	/**
	 * The applicant's own four situations, projected from the answers of §6.
	 *
	 * <p>Only reachable when the place is already being kept, which is what this method
	 * cannot know and must therefore never say: {@link ReleaseOutcome#SLOT_RELEASED} is
	 * built only where the release actually happened. So a refused order of hers whose
	 * ficha still has something in flight projects as {@code PAYMENT_IN_PROGRESS} — a
	 * checkout of hers is live, which is exactly what is holding the place — and not as a
	 * release that did not occur.
	 *
	 * <p>The verdict decides <em>whether to release</em>; it cannot decide what to say,
	 * because it deliberately does not say what a {@code SUCCESS} with nothing captured
	 * <em>means</em>. So the projection reads the status for the one distinction the
	 * table refuses to draw: {@code PAYMENT_IN_PROGRESS} is the bank still working, while
	 * {@code RETAINED_UNEXPLAINED} is an order that claims to be fine and took nothing.
	 * Telling an applicant the first when it is the second would be a claim we cannot
	 * make, and it is the reason the release path keeps its own projection instead of
	 * reading {@code Verdict} alone.
	 *
	 * <p>It is deliberately the applicant's own order that is projected, even when a
	 * sibling is what withheld the slot. Her browser asked about that order, so that is
	 * the one she can be told about: an attempt in flight somewhere else on her ficha
	 * does not change what happened to hers, and claiming otherwise would be a sentence
	 * about an order she never saw.
	 */
	private static ReleaseOutcome outcomeForOwnAttempt(EvoPaymentsGatewayPort.EvoOrderStatus status) {
		if (OrderSettlementDecider.decide(status) == OrderSettlementDecider.Verdict.CAPTURED) {
			return ReleaseOutcome.PAYMENT_CAPTURED;
		}
		return "SUCCESS".equals(status.result()) ? ReleaseOutcome.RETAINED_UNEXPLAINED
				: ReleaseOutcome.PAYMENT_IN_PROGRESS;
	}
}
