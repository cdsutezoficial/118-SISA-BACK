package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the EVO (Mastercard gateway) Hosted Checkout integration cannot
 * do its job: gateway credentials not configured, network/HTTP failure, or a
 * rejected {@code result} from EVO. Maps to HTTP 502 (BAD_GATEWAY) — the
 * payment provider is an upstream dependency; the applicant cannot "fix" a
 * processor outage by retrying the request.
 *
 * <p>{@link #orderMayHaveBeenCreated()} is not decoration. The quota claim taken
 * before the gateway call has to be released or kept depending on this flag, and
 * guessing wrong in either direction is expensive: releasing a slot that Evo
 * still holds an order for lets a later capture oversell the career, while
 * keeping a slot for an order that was never created costs the career one place
 * until its payment window closes.
 *
 * <p>The default is {@code false} — "no order exists" — because a throw site that
 * has not thought about it is far more likely to be a local precondition
 * failure (bad credentials) than a half-sent request. The two cases that genuinely
 * cannot know are named explicitly at their throw sites.
 */
public class EvoPaymentGatewayException extends RuntimeException {

	private final boolean orderMayHaveBeenCreated;

	public EvoPaymentGatewayException(String message) {
		this(message, null, false);
	}

	public EvoPaymentGatewayException(String message, Throwable cause) {
		this(message, cause, false);
	}

	private EvoPaymentGatewayException(String message, Throwable cause, boolean orderMayHaveBeenCreated) {
		super(message, cause);
		this.orderMayHaveBeenCreated = orderMayHaveBeenCreated;
	}

	/**
	 * A failure where the request may have reached EVO and created an order
	 * anyway: a network error, a timeout, or a response that reported success but
	 * carried no session.
	 *
	 * <p>Callers must treat this as "the order may exist" and must not hand back a
	 * quota slot on it — the payment can still be captured, and that ficha has to
	 * keep its place. Reconciling those orders is a separate job, not something
	 * to guess at in a catch block.
	 */
	public static EvoPaymentGatewayException possiblyCreated(String message, Throwable cause) {
		return new EvoPaymentGatewayException(message, cause, true);
	}

	/**
	 * Whether an order may exist at EVO despite the failure. When {@code true} the
	 * caller must keep any quota claim and let reconciliation settle it.
	 */
	public boolean orderMayHaveBeenCreated() {
		return orderMayHaveBeenCreated;
	}
}
