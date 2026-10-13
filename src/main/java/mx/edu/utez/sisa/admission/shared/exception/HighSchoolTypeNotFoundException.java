package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code HighSchoolType} id that does
 * not resolve to an existing record (e.g. Get-by-id target). Maps to HTTP
 * 404 in the web layer's {@code GlobalExceptionHandler} — same convention as
 * {@code OutreachChannelNotFoundException}. Not the only exception this aggregate
 * needs, as of Fase 10: the uniqueness added to {@code HighSchoolType.name}
 * brought {@link DuplicateHighSchoolTypeNameException}. It remains the only one for
 * "row not found" — there are no FKs to validate here.
 */
public class HighSchoolTypeNotFoundException extends RuntimeException {

	public HighSchoolTypeNotFoundException(String message) {
		super(message);
	}
}
