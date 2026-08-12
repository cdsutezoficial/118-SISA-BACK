package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a caller-supplied {@code PaymentRate} field fails a simple
 * validation rule enforced by the use case layer: {@code amount} not greater
 * than zero (plan section 3/4). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} — same
 * domain-exception-mapped-to-status-code convention as
 * {@code InvalidPaymentConceptDataException}/{@code InvalidPlanDataException},
 * each scoped to its own aggregate's own-field validation rather than being
 * shared across aggregates.
 */
public class InvalidPaymentRateDataException extends RuntimeException {

	public InvalidPaymentRateDataException(String message) {
		super(message);
	}
}
