package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code startPeriodId} provided to {@code CreateGenerationUseCase}
 * or {@code UpdateGenerationUseCase} is missing or does not resolve to an
 * existing {@code AcademicPeriod}. Deliberately distinct from
 * {@code AcademicPeriodNotFoundException} (404): a 404 there means "the
 * Period resource itself wasn't found" (Get-by-id/Update/ChangeStatus on
 * {@code /periods}); here the *Generation* request references a bad FK —
 * same shape as {@code DivisionNotFoundException}/{@code ProgramNotFoundException}/
 * {@code PlanNotFoundException} (all 400, not resource-not-found). Maps to
 * HTTP 400 in the web layer's {@code GlobalExceptionHandler}.
 */
public class PeriodNotFoundException extends RuntimeException {

	public PeriodNotFoundException(String message) {
		super(message);
	}
}
