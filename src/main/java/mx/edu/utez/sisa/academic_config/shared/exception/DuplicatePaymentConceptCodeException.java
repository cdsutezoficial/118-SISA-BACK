package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreatePaymentConceptUseCase} or
 * {@code UpdatePaymentConceptUseCase} is invoked with a {@code code} already
 * used by another {@code PaymentConcept}, compared case-insensitively.
 * Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code DuplicatePaymentAreaCodeException}.
 */
public class DuplicatePaymentConceptCodeException extends RuntimeException {

	public DuplicatePaymentConceptCodeException(String message) {
		super(message);
	}
}