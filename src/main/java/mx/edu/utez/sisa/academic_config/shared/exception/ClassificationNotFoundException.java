package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code SubjectClassification} id
 * that does not resolve to an existing record (e.g. Get-by-id target). Maps
 * to HTTP 404 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code AcademicDivisionNotFoundException}.
 */
public class ClassificationNotFoundException extends RuntimeException {

	public ClassificationNotFoundException(String message) {
		super(message);
	}
}
