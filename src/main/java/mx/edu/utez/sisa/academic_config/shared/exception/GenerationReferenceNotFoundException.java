package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code generationId} provided to {@code CreateGroupUseCase}
 * or {@code UpdateGroupUseCase} is missing or does not resolve to an
 * existing {@code Generation}. Deliberately distinct from
 * {@code GenerationNotFoundException} (404): a 404 there means "the
 * Generation resource itself wasn't found" (Get-by-id/Update/ChangeStatus on
 * {@code /generations}); here the *Group* request references a bad FK — same
 * shape as {@code PlanNotFoundException}/{@code PeriodNotFoundException}
 * (both 400, not resource-not-found). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} (plan: {@code docs/plans/2026-07-20-generation-group.md},
 * "Group — diseño técnico resuelto (2026-07-23)").
 */
public class GenerationReferenceNotFoundException extends RuntimeException {

	public GenerationReferenceNotFoundException(String message) {
		super(message);
	}
}
