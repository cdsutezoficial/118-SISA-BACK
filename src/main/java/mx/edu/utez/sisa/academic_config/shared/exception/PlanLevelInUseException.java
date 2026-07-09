package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code RemovePlanLevelUseCase} (or
 * {@code AcademicPlan.removeLevel}) targets a
 * {@link mx.edu.utez.sisa.academic_config.domain.model.PlanLevel} that is
 * currently referenced by its own plan's {@code socialServiceMinLevelId}
 * (spec: "Rejects removing a level used as socialServiceMinLevelId"). Maps
 * to HTTP 409 in the web layer's {@code GlobalExceptionHandler} (Phase 6).
 */
public class PlanLevelInUseException extends RuntimeException {

	public PlanLevelInUseException(String message) {
		super(message);
	}
}
