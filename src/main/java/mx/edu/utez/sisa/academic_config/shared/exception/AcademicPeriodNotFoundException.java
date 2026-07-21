package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for an {@code AcademicPeriod} id that
 * does not resolve to an existing record (Get-by-id, Update, ChangeStatus
 * targets). Maps to HTTP 404 in the web layer's {@code GlobalExceptionHandler}
 * — same convention as {@code ClassificationNotFoundException}.
 */
public class AcademicPeriodNotFoundException extends RuntimeException {

	public AcademicPeriodNotFoundException(String message) {
		super(message);
	}
}
