package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreatePaymentAreaUseCase} or
 * {@code UpdatePaymentAreaUseCase} is invoked with a {@code code} already used
 * by another {@code PaymentArea}. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} — same convention as
 * {@code DuplicateDivisionCodeException}.
 */
public class DuplicatePaymentAreaCodeException extends RuntimeException {

	public DuplicatePaymentAreaCodeException(String message) {
		super(message);
	}
}
