package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code Group} id that does not
 * resolve to an existing record (Get-by-id, Update, ChangeStatus targets).
 * Maps to HTTP 404 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code GenerationNotFoundException}.
 */
public class GroupNotFoundException extends RuntimeException {

	public GroupNotFoundException(String message) {
		super(message);
	}
}
