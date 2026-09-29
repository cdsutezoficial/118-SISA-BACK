package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Too many {@code POST /candidates/payment-access} attempts from one client
 * ({@code 429 Too Many Requests}).
 *
 * <p>That endpoint proves identity with {@code folio + last 3 CURP
 * characters}, a deliberately weak pair (sequential folios, ~46k suffixes), so
 * it is throttled per client IP — see
 * {@code PaymentAccessRateLimiter}. Only this endpoint uses the exception.
 */
public class TooManyPaymentAccessAttemptsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TooManyPaymentAccessAttemptsException(String message) {
		super(message);
	}
}
