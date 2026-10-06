package mx.edu.utez.sisa.admission.domain.port.in;

/**
 * Nightly reconciliation of every {@code CheckoutAttempt} that is still open, per
 * {@code decision-cupo-proceso-admision.md} §3.7 and §6.
 *
 * <p>This is the safety net under the whole quota mechanism. The browser may report a
 * timeout that never happened, may vanish without reporting anything, and a process
 * can die between opening an attempt and answering the applicant. Any of those leaves a
 * row asking the same question — <em>did this order ever take money?</em> — and the only
 * party that can answer it is the bank.
 *
 * <p>It runs before {@code VENCEN_FICHAS} so that a captured payment is recorded as
 * {@code PAID} before the expiry sweep decides the ficha missed its deadline. The order
 * of those two jobs is not cosmetic: the other way round, a payment that landed at 23:59
 * is marked {@code PAYMENT_EXPIRED} on a ficha whose money already arrived.
 */
public interface ReconcileFichaPaymentsUseCase {

	/**
	 * Asks the gateway about every open attempt and settles what it can settle.
	 *
	 * <p>The unit of decision is the <b>ficha</b>, not the attempt: one ficha can own
	 * several open attempts and the money is hers. Every attempt of a ficha is asked
	 * about before anything is written, and the writes that follow come from the folded
	 * verdict — so an order refused last week can never hand back a place that a sibling
	 * attempt captured. Decided attempt by attempt it did exactly that, which is how a
	 * paid ficha ended up holding no slot.
	 *
	 * <p>The counts are therefore not all in the same unit, and the names say which is
	 * which: {@code settledCaptures} and {@code releasedSlots} count <em>fichas</em> (one
	 * place each, one payment each), while {@code closedRows}, {@code heldAttempts} and
	 * {@code failedAttempts} count <em>attempts</em>, because the history and the retry
	 * list are both written one attempt at a time.
	 *
	 * @return what happened, for the job to log
	 */
	ReconciliationResult reconcile();

	/**
	 * Counts rather than the attempts themselves: the job logs only when something
	 * moved, and a nightly line listing a thousand held attempts would be noise.
	 *
	 * @param settledCaptures fichas the bank paid, now {@code PAID} with their place kept
	 * @param releasedSlots   fichas whose place went back, once every open attempt of
	 *                        theirs had been definitively refused
	 * @param closedRows      attempt rows the sweep closed for any reason; per attempt,
	 *                        since that is the unit the history is written in
	 * @param heldAttempts    attempt rows left open because the bank was not conclusive
	 * @param failedAttempts  attempt rows whose lookup failed; nothing was written for the
	 *                        ficha they belong to
	 */
	record ReconciliationResult(int settledCaptures, int releasedSlots, int closedRows, int heldAttempts,
			int failedAttempts) {

		/** Whether anything at all changed, so a quiet night stays quiet. */
		public boolean changedAnything() {
			return settledCaptures > 0 || releasedSlots > 0 || closedRows > 0;
		}
	}
}