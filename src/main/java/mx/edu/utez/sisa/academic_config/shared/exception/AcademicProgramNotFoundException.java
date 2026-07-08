package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for an {@code AcademicProgram} id that
 * does not resolve to an existing record (e.g. Get/Update/ChangeStatus
 * target). Maps to HTTP 404 in the web layer's {@code GlobalExceptionHandler}
 * (Phase 6).
 */
public class AcademicProgramNotFoundException extends RuntimeException {

	public AcademicProgramNotFoundException(String message) {
		super(message);
	}
}
