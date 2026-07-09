package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code programId} provided to {@code CreateAcademicPlanUseCase}
 * is missing or does not resolve to an existing {@code AcademicProgram}
 * (spec: "Rejects a programId that does not exist"). Deliberately distinct
 * from {@code AcademicProgramNotFoundException} (404): a 404 there means "the
 * Program resource itself wasn't found"; here the *Plan* request references a
 * bad FK — same shape as {@code DivisionNotFoundException} (400), not a
 * resource-not-found. Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} (Phase 6).
 */
public class ProgramNotFoundException extends RuntimeException {

	public ProgramNotFoundException(String message) {
		super(message);
	}
}
