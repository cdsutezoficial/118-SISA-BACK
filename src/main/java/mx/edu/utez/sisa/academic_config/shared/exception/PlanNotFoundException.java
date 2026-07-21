package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code planId} provided to {@code CreateGenerationUseCase} or
 * {@code UpdateGenerationUseCase} is missing or does not resolve to an
 * existing {@code AcademicPlan} (same shape as {@code CreateAcademicPlanUseCase}'s
 * missing/unknown {@code programId} check). Deliberately distinct from
 * {@code AcademicPlanNotFoundException} (404): a 404 there means "the Plan
 * resource itself wasn't found" (Get-by-id/Update on {@code /plans}); here
 * the *Generation* request references a bad FK — same shape as
 * {@code DivisionNotFoundException}/{@code ProgramNotFoundException} (both
 * 400, not resource-not-found). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler}.
 */
public class PlanNotFoundException extends RuntimeException {

	public PlanNotFoundException(String message) {
		super(message);
	}
}
