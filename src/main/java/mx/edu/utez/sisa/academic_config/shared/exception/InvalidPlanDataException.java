package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a caller-supplied {@code AcademicPlan}/{@code PlanLevel} field
 * fails a simple range validation rule enforced by the use case layer:
 * {@code minPassingGrade} outside {@code [0, 10]} ({@code
 * CreateAcademicPlanUseCase}) or {@code levelNumber} outside
 * {@code [1, totalLevels]} ({@code AddPlanLevelUseCase}). Maps to HTTP 400 in
 * the web layer's {@code GlobalExceptionHandler}.
 *
 * <p>
 * NOTE (bugfix): both call sites previously threw a plain
 * {@code IllegalArgumentException}, which neither this module's nor
 * {@code identity}'s {@code GlobalExceptionHandler} maps explicitly — it fell
 * through to the generic catch-all handler and produced an incorrect
 * {@code 500 Internal Server Error} instead of {@code 400 Bad Request}. This
 * exception follows the same domain-exception-mapped-to-status-code
 * convention as {@code InvalidSocialServiceLevelException}, reused across
 * both range checks since they are the same class of error (simple
 * caller-input range validation, not an identity/uniqueness rule).
 */
public class InvalidPlanDataException extends RuntimeException {

	public InvalidPlanDataException(String message) {
		super(message);
	}
}
