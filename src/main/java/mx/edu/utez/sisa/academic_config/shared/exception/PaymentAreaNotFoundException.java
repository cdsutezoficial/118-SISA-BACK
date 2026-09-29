package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code PaymentArea} id that does not
 * resolve to an existing record (e.g. Get-by-id target). Maps to HTTP 404 in
 * the web layer's {@code GlobalExceptionHandler}.
 */
public class PaymentAreaNotFoundException extends RuntimeException {

	public PaymentAreaNotFoundException(String message) {
		super(message);
	}
}
