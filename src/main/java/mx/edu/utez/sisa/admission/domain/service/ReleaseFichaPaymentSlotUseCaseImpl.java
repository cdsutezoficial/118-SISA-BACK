package mx.edu.utez.sisa.admission.domain.service;

import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
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
 */
public class ReleaseFichaPaymentSlotUseCaseImpl implements ReleaseFichaPaymentSlotUseCase {

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	public ReleaseFichaPaymentSlotUseCaseImpl(AdmissionPaymentRepository admissionPaymentRepository,
			EvoPaymentsGatewayPort evoPaymentsGateway, CheckoutSlotClaimer checkoutSlotClaimer) {
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
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

		EvoPaymentsGatewayPort.EvoOrderStatus status = evoPaymentsGateway.retrieveOrder(orderId);
		ReleaseOutcome outcome = toOutcome(OrderSettlementDecider.decide(status), status);

		if (outcome != ReleaseOutcome.SLOT_RELEASED) {
			// Nothing is written. The attempt stays open on purpose so the daily sweep
			// asks the bank again: closing it on a "we don't know yet" would be the one
			// way to lose a capture that landed after this call.
			return new ReleaseResult(candidateId, orderId, outcome, false);
		}

		checkoutSlotClaimer.release(candidateId);
		// REJECTED, not SESSION_TIMEOUT/ERROR: those record what the browser saw, and
		// this row is being closed on what the bank said. An order did exist and was
		// refused — that is the fact worth keeping in the history.
		checkoutSlotClaimer.closeAttempt(orderId, CheckoutAttemptCloseReason.REJECTED);
		return new ReleaseResult(candidateId, orderId, ReleaseOutcome.SLOT_RELEASED, true);
	}

	/**
	 * The applicant's four situations, projected from the three answers of §6.
	 *
	 * <p>The verdict decides <em>whether to release</em>; it cannot decide what to say,
	 * because it deliberately does not say what a {@code SUCCESS} with nothing captured
	 * <em>means</em>. So the projection reads the status for the one distinction the
	 * table refuses to draw: {@code PAYMENT_IN_PROGRESS} is the bank still working, while
	 * {@code RETAINED_UNEXPLAINED} is an order that claims to be fine and took nothing.
	 * Telling an applicant the first when it is the second would be a claim we cannot
	 * make, and it is the reason the release path keeps its own projection instead of
	 * reading {@code Verdict} alone.
	 */
	private static ReleaseOutcome toOutcome(OrderSettlementDecider.Verdict verdict,
			EvoPaymentsGatewayPort.EvoOrderStatus status) {
		if (verdict == OrderSettlementDecider.Verdict.CAPTURED) {
			return ReleaseOutcome.PAYMENT_CAPTURED;
		}
		if (verdict == OrderSettlementDecider.Verdict.RELEASEABLE) {
			return ReleaseOutcome.SLOT_RELEASED;
		}
		return "SUCCESS".equals(status.result()) ? ReleaseOutcome.RETAINED_UNEXPLAINED
				: ReleaseOutcome.PAYMENT_IN_PROGRESS;
	}
}
