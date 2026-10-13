package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a use case is invoked for an {@code OutreachChannel} id that
 * does not resolve to an existing record (e.g. Get-by-id target). Maps to
 * HTTP 404 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code academic_config.ClassificationNotFoundException}.
 * <p>Not the only exception this aggregate needs, as of Fase 9: the uniqueness
 * added to {@code OutreachChannel.name} brought
 * {@link DuplicateOutreachChannelNameException}. It remains the only one for
 * "row not found" — there are no FKs to validate here, and the original
 * no-uniqueness decision of {@code docs/plans/2026-07-28-outreach-channel.md}
 * no longer applies.
 */
public class OutreachChannelNotFoundException extends RuntimeException {

	public OutreachChannelNotFoundException(String message) {
		super(message);
	}
}
