package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code HighSchoolType} id that does
 * not resolve to an existing record (e.g. Get-by-id target). Maps to HTTP
 * 404 in the web layer's {@code GlobalExceptionHandler} — same convention as
 * {@code OutreachChannelNotFoundException}. The only exception this
 * aggregate needs: no FKs to validate, no uniqueness to protect.
 */
public class HighSchoolTypeNotFoundException extends RuntimeException {

	public HighSchoolTypeNotFoundException(String message) {
		super(message);
	}
}
