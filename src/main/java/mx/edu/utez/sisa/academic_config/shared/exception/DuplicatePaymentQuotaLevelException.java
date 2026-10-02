package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when an ACTIVE {@code PaymentConcept} of type
 * {@code PERIODIC_QUOTA} already exists for the requested {@code levelNumber}.
 * Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code DuplicatePaymentRateException}.
 *
 * <p>
 * One active recurring quota per level is not cosmetic. The tuition lookup
 * resolves a student's price from "the active periodic quota whose
 * {@code levelNumber} equals the student's current level, priced for their
 * program"; two such concepts would make the price ambiguous and, like the
 * admission ticket's {@code ADMISSION_CONCEPT_AMBIGUOUS}, the right answer is to
 * refuse rather than silently pick one.
 */
public class DuplicatePaymentQuotaLevelException extends RuntimeException {

	public DuplicatePaymentQuotaLevelException(String message) {
		super(message);
	}
}