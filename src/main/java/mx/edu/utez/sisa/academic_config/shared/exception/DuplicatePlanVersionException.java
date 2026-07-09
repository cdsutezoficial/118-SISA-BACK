package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicPlanUseCase}/{@code UpdateAcademicPlanUseCase}
 * is invoked with a {@code version} already used by another
 * {@code AcademicPlan} of the same {@code programId} (spec: "Rejects
 * duplicate version within the same program" / "Rejects update to a version
 * already used within the same program"). {@code version} uniqueness is
 * scoped to {@code programId}, not global — the same version reused across
 * different programs MUST NOT trigger this exception (spec: "Same version
 * reused across different programs succeeds"). Maps to HTTP 409 in the web
 * layer's {@code GlobalExceptionHandler} (Phase 6).
 *
 * <p>
 * NOTE (apply-phase deviation): this exception was not part of the original
 * 9-exception list in tasks.md §2.4 / design.md's File Changes table — that
 * list omitted an exception for this spec requirement ("version MUST be
 * unique within the same programId", with multiple explicit reject
 * scenarios). Added here as a 10th exception, following the same
 * {@code Duplicate*Exception} → 409 convention as
 * {@code DuplicateLevelNumberException}/{@code DuplicateSubjectCodeException},
 * to avoid silently skipping a binding spec requirement.
 */
public class DuplicatePlanVersionException extends RuntimeException {

	public DuplicatePlanVersionException(String message) {
		super(message);
	}
}
