package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for an {@code AcademicDivision} id that
 * does not resolve to an existing record (e.g. Update/ChangeStatus target).
 * Maps to HTTP 404 in the web layer's {@code GlobalExceptionHandler} (Phase
 * 5).
 */
public class AcademicDivisionNotFoundException extends RuntimeException {

	public AcademicDivisionNotFoundException(String message) {
		super(message);
	}
}
