package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when {@code CreateHighSchoolTypeUseCase} or
 * {@code UpdateHighSchoolTypeUseCase} is invoked with a {@code name} that
 * normalizes to one already used by another {@code HighSchoolType}. Maps to
 * HTTP 409 in the admission web layer's {@code GlobalExceptionHandler}
 * (Fase 10), same shape as {@link DuplicateOutreachChannelNameException} in the
 * sibling catalog.
 *
 * <p>"Another" means a <b>different</b> row: the update use case excludes the type
 * being edited, so saving an unchanged entry from the list is not a conflict.
 */
public class DuplicateHighSchoolTypeNameException extends RuntimeException {

	public DuplicateHighSchoolTypeNameException(String message) {
		super(message);
	}
}
