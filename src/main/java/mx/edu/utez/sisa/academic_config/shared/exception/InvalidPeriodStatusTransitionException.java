package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown by {@code AcademicPeriod#changeStatus} when the requested target
 * status is not the immediate next status in the PO-confirmed (2026-07-20)
 * strictly sequential, forward-only lifecycle
 * {@code CONFIGURATION -> ENROLLMENT -> ACTIVE -> CLOSED} — covers both
 * skips (e.g. {@code CONFIGURATION -> ACTIVE}) and backward moves (e.g.
 * {@code CLOSED -> ACTIVE}); {@code CLOSED} is terminal, so ANY transition
 * requested from it fails. Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} — this is a caller-input validation error,
 * not a not-found/conflict, same class of error as {@code InvalidPlanDataException}
 * but kept as its own type since it names both the attempted transition and
 * the reason it's invalid, which is specific enough to this state machine to
 * not be worth folding into the generic range-validation exception.
 */
public class InvalidPeriodStatusTransitionException extends RuntimeException {

	public InvalidPeriodStatusTransitionException(String message) {
		super(message);
	}
}
