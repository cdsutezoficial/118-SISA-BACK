package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a rate-set reconciliation on a {@code PERIODIC_QUOTA} concept does
 * not price every ACTIVE {@code AcademicProgram}. Maps to HTTP 409 in the web
 * layer's {@code GlobalExceptionHandler}.
 *
 * <p>
 * A recurring quota is per level and covers the whole student body, so a program
 * left unpriced is a student who reaches the counter with no number to charge.
 * The user-removes-a-rate path is therefore unavailable for this concept type:
 * the only way a rate goes {@code INACTIVE} is its program ceasing to be active.
 * The message carries the offending program names so the caller can fix the
 * payload without a second round trip.
 */
public class IncompletePaymentRateSetException extends RuntimeException {

	public IncompletePaymentRateSetException(String message) {
		super(message);
	}
}