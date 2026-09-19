package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreatePaymentAreaUseCase} or
 * {@code UpdatePaymentAreaUseCase} is invoked with a {@code name} already used
 * by another {@code PaymentArea}. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} — same convention as
 * {@code DuplicateDivisionNameException}.
 */
public class DuplicatePaymentAreaNameException extends RuntimeException {

	public DuplicatePaymentAreaNameException(String message) {
		super(message);
	}
}
