package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicDivisionUseCase} or
 * {@code UpdateAcademicDivisionUseCase} is invoked with a {@code code}
 * already used by another {@code AcademicDivision} (spec: "Rejects duplicate
 * code" / "Rejects update to a code already used by another division").
 * Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler} (Phase
 * 5).
 */
public class DuplicateDivisionCodeException extends RuntimeException {

	public DuplicateDivisionCodeException(String message) {
		super(message);
	}
}
