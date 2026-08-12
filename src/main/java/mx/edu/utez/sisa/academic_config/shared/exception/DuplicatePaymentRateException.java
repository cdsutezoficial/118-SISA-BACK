package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code SetPaymentRateUseCase} is invoked with a {@code periodId}
 * for which a rate already exists on the EXACT same
 * {@code (conceptId, programId, level, periodId)} combination (plan section
 * 2/4 — period-scoped rates are independent casillas, each unique per exact
 * combination, unlike continuous rates which get closed and replaced
 * instead). Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler}
 * — same convention as {@code DuplicateGenerationNumberException}.
 */
public class DuplicatePaymentRateException extends RuntimeException {

	public DuplicatePaymentRateException(String message) {
		super(message);
	}
}
