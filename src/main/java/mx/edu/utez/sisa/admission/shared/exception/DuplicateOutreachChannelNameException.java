package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when {@code CreateOutreachChannelUseCase} or
 * {@code UpdateOutreachChannelUseCase} is invoked with a {@code name} that
 * normalizes to one already used by another {@code OutreachChannel} (spec:
 * "Rejects duplicate normalized name"). Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} (Fase 9).
 *
 * <p>"Another" means a <b>different</b> row: the update use case excludes the
 * channel being edited, so renaming a channel to the name it already has is not
 * a conflict. Same convention as
 * {@code UpdateGroupUseCaseImpl}'s self-exclusion filter.
 */
public class DuplicateOutreachChannelNameException extends RuntimeException {

	public DuplicateOutreachChannelNameException(String message) {
		super(message);
	}
}
