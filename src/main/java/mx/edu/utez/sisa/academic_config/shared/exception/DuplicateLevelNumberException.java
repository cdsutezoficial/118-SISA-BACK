package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code AddPlanLevelUseCase}/{@code UpdatePlanLevelUseCase} (or
 * {@code AcademicPlan.addLevel}/{@code updateLevel}) would result in two
 * {@link mx.edu.utez.sisa.academic_config.domain.model.PlanLevel} records
 * sharing the same {@code levelNumber} within the same plan (spec: "Rejects
 * duplicate levelNumber within the same plan"). Maps to HTTP 409 in the web
 * layer's {@code GlobalExceptionHandler} (Phase 6).
 */
public class DuplicateLevelNumberException extends RuntimeException {

	public DuplicateLevelNumberException(String message) {
		super(message);
	}
}
