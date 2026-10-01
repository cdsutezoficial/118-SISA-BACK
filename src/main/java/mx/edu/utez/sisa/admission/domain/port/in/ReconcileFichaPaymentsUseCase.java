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
	 * @return what happened, for the job to log
	 */
	ReconciliationResult reconcile();

	/**
	 * Counts rather than the attempts themselves: the job logs only when something
	 * moved, and a nightly line listing a thousand held attempts would be noise.
	 *
	 * @param settledCaptures attempts the bank said took money, now {@code PAID}
	 * @param releasedSlots   attempts definitively refused; their place went back
	 * @param closedRows      attempts the sweep closed for any reason
	 * @param heldAttempts    attempts left open because the bank was not conclusive
	 * @param failedAttempts  attempts whose lookup failed; nothing was written for them
	 */
	record ReconciliationResult(int settledCaptures, int releasedSlots, int closedRows, int heldAttempts,
			int failedAttempts) {

		/** Whether anything at all changed, so a quiet night stays quiet. */
		public boolean changedAnything() {
			return settledCaptures > 0 || releasedSlots > 0 || closedRows > 0;
		}
	}
}