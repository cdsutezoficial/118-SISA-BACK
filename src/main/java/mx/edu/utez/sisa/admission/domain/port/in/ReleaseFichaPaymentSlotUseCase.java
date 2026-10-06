package mx.edu.utez.sisa.admission.domain.port.in;

import java.util.UUID;

/**
 * Settles one payment attempt when the applicant's browser gave up on it, and hands
 * the quota slot back <em>only if the gateway proves nothing was captured</em>.
 * Governs {@code POST /candidates/{id}/payments/release}, called by the portal from
 * {@code onEvoTimeout} and {@code onEvoError}.
 *
 * <p>Why it asks the bank instead of trusting the caller: those two callbacks report
 * what happened <em>in the browser</em>, and neither is proof of anything at EVO. A
 * timeout is the textbook ambiguous failure — the request may have been received and
 * captured seconds later — and releasing on the browser's word would hand the last
 * place of a career to a second applicant while the first payment is still live.
 * That is the oversell this whole slice exists to prevent, and it would be caused by
 * the very endpoint meant to fix a stuck slot. So this use case treats the browser
 * report as <b>a question, not an answer</b>: it asks {@code Retrieve Order} and
 * decides from the reply.
 *
 * <p>The decision is the same table the daily sweep applies ({@code §6}), because it
 * is the same question — "may this money still be taken?" — asked about one attempt
 * instead of all of them:
 *
 * <table>
 * <caption>What the gateway answers and what happens to the slot</caption>
 * <tr><th>Retrieve Order</th><th>Slot</th><th>Outcome</th></tr>
 * <tr><td>{@code FAILURE}, or an error node, with no capture</td><td>released</td>
 *     <td>{@link ReleaseOutcome#SLOT_RELEASED}</td></tr>
 * <tr><td>captured anything</td><td>kept</td><td>{@link ReleaseOutcome#PAYMENT_CAPTURED}</td></tr>
 * <tr><td>{@code SUCCESS} with no capture</td><td>kept</td>
 *     <td>{@link ReleaseOutcome#RETAINED_UNEXPLAINED}</td></tr>
 * <tr><td>{@code PENDING} / unknown</td><td>kept</td>
 *     <td>{@link ReleaseOutcome#PAYMENT_IN_PROGRESS}</td></tr>
 * </table>
 *
 * <p>Only the first row writes. It also closes the {@code CheckoutAttempt} with
 * {@code REJECTED}, because at that point there is a real fact to record: an order
 * existed and the bank refused it. The other three deliberately leave the attempt
 * <b>open</b>, so the daily sweep keeps looking — closing on a guess is how the
 * capture-later case would be lost.
 *
 * <p>Rejections, all before any gateway call: candidate/payment missing (404);
 * already paid (409 — a paid ficha holds its place permanently and there is nothing
 * to give back); the {@code orderId} does not match the one persisted for this ficha
 * (400 — you can only settle your own attempt, which is what makes the endpoint safe
 * to expose publicly). A gateway outage propagates as
 * {@code EvoPaymentGatewayException} (502) and changes nothing, so the applicant can
 * simply try again and the sweep covers what they do not.
 */
public interface ReleaseFichaPaymentSlotUseCase {

	/**
	 * @param candidateId the ficha whose slot may be handed back
	 * @param orderId     the attempt the browser was working on; must be the order
	 *                    persisted for this ficha, so one applicant cannot release
	 *                    another's claim
	 * @return what the gateway said and whether the slot was actually freed — the
	 *         caller needs both, because "still paying" and "nothing happened" are
	 *         opposite situations that both end in no release
	 */
	ReleaseResult release(UUID candidateId, String orderId);

	/**
	 * The four answers, as something the portal can branch on. Kept as an enum rather
	 * than prose because the front has to say something different for each: a freed
	 * slot invites a retry, a captured payment is money that already arrived, and the
	 * last two are "we cannot know yet".
	 */
	enum ReleaseOutcome {

		/** The bank refused and nothing was captured: the slot is free again. */
		SLOT_RELEASED,

		/** Still {@code PENDING} at the bank. The slot stays held — the applicant may be paying right now. */
		PAYMENT_IN_PROGRESS,

		/** Money was captured. The slot stays held; the sweep marks the ficha paid. */
		PAYMENT_CAPTURED,

		/** {@code SUCCESS} with no capture. Not diagnosable ({@code §6}), so it is retained for the expiry sweep. */
		RETAINED_UNEXPLAINED
	}

	record ReleaseResult(UUID candidateId, String orderId, ReleaseOutcome outcome, boolean slotReleased) {
	}
}
