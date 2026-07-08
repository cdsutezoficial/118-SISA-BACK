package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicProgramUseCase} or
 * {@code UpdateAcademicProgramUseCase} is invoked with a {@code code} already
 * used by another {@code AcademicProgram} (spec: "Rejects duplicate code" /
 * "Rejects update to a code already used by another program"). Maps to HTTP
 * 409 in the web layer's {@code GlobalExceptionHandler} (Phase 6).
 */
public class DuplicateProgramCodeException extends RuntimeException {

	public DuplicateProgramCodeException(String message) {
		super(message);
	}
}
