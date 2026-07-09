package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code levelId} does not resolve to an existing
 * {@link mx.edu.utez.sisa.academic_config.domain.model.PlanLevel} owned by
 * the target {@code AcademicPlan} — includes the case where the id belongs
 * to a different plan entirely (spec: "Rejects planLevelId from a different
 * plan"). Maps to HTTP 404 in the web layer's {@code GlobalExceptionHandler}
 * (Phase 6).
 */
public class PlanLevelNotFoundException extends RuntimeException {

	public PlanLevelNotFoundException(String message) {
		super(message);
	}
}
