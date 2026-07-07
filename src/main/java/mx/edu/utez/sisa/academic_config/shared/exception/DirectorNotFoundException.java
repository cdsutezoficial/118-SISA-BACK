package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code directorPersonId} provided to
 * {@code CreateAcademicDivisionUseCase} or {@code UpdateAcademicDivisionUseCase}
 * does not resolve to an existing {@code Person} (spec: "Rejects
 * non-existent director"). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} (Phase 5).
 */
public class DirectorNotFoundException extends RuntimeException {

	public DirectorNotFoundException(String message) {
		super(message);
	}
}
