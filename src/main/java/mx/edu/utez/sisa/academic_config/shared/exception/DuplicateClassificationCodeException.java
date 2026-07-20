package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateSubjectClassificationUseCase} is invoked with a
 * {@code code} already used by another {@code SubjectClassification} (spec:
 * "Rejects duplicate code"). Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} — same pattern as
 * {@code DuplicateDivisionCodeException}/{@code DuplicateProgramCodeException}.
 */
public class DuplicateClassificationCodeException extends RuntimeException {

	public DuplicateClassificationCodeException(String message) {
		super(message);
	}
}
