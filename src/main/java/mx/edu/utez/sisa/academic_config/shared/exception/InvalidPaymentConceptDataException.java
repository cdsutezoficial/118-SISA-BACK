package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a caller-supplied {@code PaymentConcept} field fails a simple
 * range validation rule enforced by the use case layer:
 * {@code maxPerStudent}/{@code maxPerPeriod} not greater than zero when
 * provided, or {@code availableFrom} after {@code availableUntil} when both
 * are provided (plan section 4). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} — same
 * domain-exception-mapped-to-status-code convention as
 * {@code InvalidPlanDataException}.
 */
public class InvalidPaymentConceptDataException extends RuntimeException {

	public InvalidPaymentConceptDataException(String message) {
		super(message);
	}
}
