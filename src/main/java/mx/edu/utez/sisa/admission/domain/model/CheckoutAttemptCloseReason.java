package mx.edu.utez.sisa.admission.domain.model;

/**
 * Why a {@link CheckoutAttempt} stopped being an open question, per
 * {@code decision-cupo-proceso-admision.md} §3.7.
 *
 * <p>{@link #STARTED} is the initial value, not a closure: it means "the attempt
 * was opened and nothing has decided it yet". Everything else is terminal, and
 * the value is kept <em>on the row</em> rather than recomputed, because the
 * whole point of the table is that the answer to "what happened to this
 * payment?" survives in the database instead of having to be re-derived from a
 * gateway that has long forgotten.
 *
 * <p>The order the gateway failed in is preserved rather than flattened into one
 * "failed" value, because it decides the quota:
 * <ul>
 * <li>{@link #ORDER_NOT_CREATED} — Evo refused before any order existed. The
 * slot goes straight back; there is nothing at the bank that could be
 * captured.</li>
 * <li>{@link #REJECTED} — an order did exist and the bank answered
 * {@code FAILURE}. Same consequence for the slot, but a different fact: there is
 * an order id to look up, and the attempt is in the history because somebody
 * created it.</li>
 * <li>{@link #CAPTURED} — the money is in. A ficha in this state is never
 * released, whatever the slot math says.</li>
 * </ul>
 */
public enum CheckoutAttemptCloseReason {

	/** Opened, still undecided. The only non-terminal value. */
	STARTED,

	/** The bank's session timed out in the browser and the applicant gave up (§3.5). */
	SESSION_TIMEOUT,

	/** The hosted checkout reported an error (§3.5). */
	ERROR,

	/** Evo refused before creating an order; the slot was handed straight back (§3.6). */
	ORDER_NOT_CREATED,

	/** Evo created the order and answered FAILURE for it (§6). */
	REJECTED,

	/** The order captured: the money is in and the ficha is paid (§6). */
	CAPTURED,

	/** The order expired or was cancelled without capturing (§6). */
	ORDER_EXPIRED
}