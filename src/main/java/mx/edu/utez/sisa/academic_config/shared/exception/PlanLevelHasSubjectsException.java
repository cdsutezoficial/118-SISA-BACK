package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code RemovePlanLevelUseCase} (or
 * {@code AcademicPlan.removeLevel}) targets a
 * {@link mx.edu.utez.sisa.academic_config.domain.model.PlanLevel} that still
 * has {@link mx.edu.utez.sisa.academic_config.domain.model.Subject} children
 * — it must be emptied first (spec: "RemovePlanLevelUseCase MUST reject
 * removal of a PlanLevel that is referenced by ... any Subject"). Maps to
 * HTTP 409 in the web layer's {@code GlobalExceptionHandler} (Phase 6).
 */
public class PlanLevelHasSubjectsException extends RuntimeException {

	public PlanLevelHasSubjectsException(String message) {
		super(message);
	}
}
