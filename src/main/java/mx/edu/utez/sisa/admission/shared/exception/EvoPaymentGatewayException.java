package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the EVO (Mastercard gateway) Hosted Checkout integration cannot
 * do its job: gateway credentials not configured, network/HTTP failure, or a
 * rejected {@code result} from EVO. Maps to HTTP 502 (BAD_GATEWAY) — the
 * payment provider is an upstream dependency; the applicant cannot "fix" a
 * processor outage by retrying the request.
 */
public class EvoPaymentGatewayException extends RuntimeException {

	public EvoPaymentGatewayException(String message) {
		super(message);
	}

	public EvoPaymentGatewayException(String message, Throwable cause) {
		super(message, cause);
	}
}