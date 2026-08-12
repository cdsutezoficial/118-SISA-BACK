package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code OpenProgramAdmissionUseCase}/
 * {@code UpdateProgramAdmissionConfigUseCase} is invoked with a
 * {@code (programId, periodId)} pair already used by another
 * {@code ProgramAdmissionConfig} (docs: {@code 02-config-academica.md} line
 * 224, "Restricción única: (programId, periodId)" — a program cannot have two
 * admission configurations targeting the same destination period). Maps to
 * HTTP 409 in the web layer's {@code GlobalExceptionHandler} — same pattern as
 * {@code DuplicateGenerationNumberException}.
 */
public class DuplicateProgramAdmissionConfigException extends RuntimeException {

	public DuplicateProgramAdmissionConfigException(String message) {
		super(message);
	}
}
