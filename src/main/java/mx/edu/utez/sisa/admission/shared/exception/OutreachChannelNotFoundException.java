package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a use case is invoked for an {@code OutreachChannel} id that
 * does not resolve to an existing record (e.g. Get-by-id target). Maps to
 * HTTP 404 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code academic_config.ClassificationNotFoundException}.
 * The only exception this aggregate needs: no FKs to validate, no uniqueness
 * to protect (see {@code docs/plans/2026-07-28-outreach-channel.md}).
 */
public class OutreachChannelNotFoundException extends RuntimeException {

	public OutreachChannelNotFoundException(String message) {
		super(message);
	}
}
