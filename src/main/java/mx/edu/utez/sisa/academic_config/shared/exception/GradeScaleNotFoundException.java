package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code scaleId} does not resolve to an existing
 * {@link mx.edu.utez.sisa.academic_config.domain.model.GradeScale} owned by
 * the target {@code AcademicPlan} (spec: "Update/Remove Grade Scale" target
 * not found) — same convention as {@code PlanLevelNotFoundException}. Maps to
 * HTTP 404 in the web layer's {@code GlobalExceptionHandler}.
 */
public class GradeScaleNotFoundException extends RuntimeException {

	public GradeScaleNotFoundException(String message) {
		super(message);
	}
}
