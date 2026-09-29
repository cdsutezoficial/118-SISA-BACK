package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a caller-supplied {@code PaymentArea} field fails a simple
 * validation rule enforced by the use case layer: {@code name} or {@code code}
 * is null/blank. Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} — same convention as
 * {@code InvalidPaymentConceptDataException}.
 */
public class InvalidPaymentAreaDataException extends RuntimeException {

	public InvalidPaymentAreaDataException(String message) {
		super(message);
	}
}
