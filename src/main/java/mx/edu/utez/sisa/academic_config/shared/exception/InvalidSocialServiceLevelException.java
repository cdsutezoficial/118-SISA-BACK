package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicPlanUseCase}/{@code UpdateAcademicPlanUseCase}
 * is invoked with a {@code socialServiceMinLevelId} that is invalid for the
 * given {@code requiresSocialService} value or that does not reference a
 * {@link mx.edu.utez.sisa.academic_config.domain.model.PlanLevel} belonging
 * to this same {@code AcademicPlan} (spec: "Rejects
 * requiresSocialService=true with a non-null socialServiceMinLevelId at
 * creation" / "Rejects socialServiceMinLevelId referencing a PlanLevel from a
 * different plan"). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} (Phase 6).
 */
public class InvalidSocialServiceLevelException extends RuntimeException {

	public InvalidSocialServiceLevelException(String message) {
		super(message);
	}
}
