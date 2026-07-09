package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code subjectId} does not resolve to an existing
 * {@link mx.edu.utez.sisa.academic_config.domain.model.Subject} owned by the
 * target {@code AcademicPlan}. Maps to HTTP 404 in the web layer's
 * {@code GlobalExceptionHandler} (Phase 6).
 */
public class SubjectNotFoundException extends RuntimeException {

	public SubjectNotFoundException(String message) {
		super(message);
	}
}
