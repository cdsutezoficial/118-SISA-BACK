package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateGenerationUseCase}/{@code UpdateGenerationUseCase}
 * is invoked with a {@code number} already used by another {@code Generation}
 * of the SAME program (PO-confirmed 2026-07-20: {@code number} is a
 * per-program sequential counter that never resets and never repeats — there
 * is deliberately NO uniqueness on {@code (programId, year)}, since a program
 * can open more than one generation in the same calendar year). Maps to HTTP
 * 409 in the web layer's {@code GlobalExceptionHandler} — same pattern as
 * {@code DuplicatePeriodException}.
 */
public class DuplicateGenerationNumberException extends RuntimeException {

	public DuplicateGenerationNumberException(String message) {
		super(message);
	}
}
