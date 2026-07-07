package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicDivisionUseCase} or
 * {@code UpdateAcademicDivisionUseCase} is invoked with a {@code name}
 * already used by another {@code AcademicDivision} (spec: "Rejects duplicate
 * name"). Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler}
 * (Phase 5).
 */
public class DuplicateDivisionNameException extends RuntimeException {

	public DuplicateDivisionNameException(String message) {
		super(message);
	}
}
