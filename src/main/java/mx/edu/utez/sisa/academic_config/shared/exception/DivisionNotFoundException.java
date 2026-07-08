package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code divisionId} provided to
 * {@code CreateAcademicProgramUseCase} or {@code UpdateAcademicProgramUseCase}
 * is missing or does not resolve to an existing {@code AcademicDivision}
 * (spec: "Rejects missing divisionId" / "Rejects a divisionId that does not
 * exist"). Deliberately distinct from
 * {@code AcademicDivisionNotFoundException} (404, design.md — Decision:
 * "Division-not-found exception"): a 404 there means "the Division resource
 * itself wasn't found"; here the *Program* request references a bad FK — same
 * shape as {@code DirectorNotFoundException} (400), not a resource-not-found.
 * Maps to HTTP 400 in the web layer's {@code GlobalExceptionHandler} (Phase
 * 6).
 */
public class DivisionNotFoundException extends RuntimeException {

	public DivisionNotFoundException(String message) {
		super(message);
	}
}
