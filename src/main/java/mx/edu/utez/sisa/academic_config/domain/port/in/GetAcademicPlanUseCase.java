package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicPlan} by id, including its full
 * {@code PlanLevel}/{@code Subject} child graph (spec: "Get Academic Plan by
 * Id"). Reuses {@link AcademicPlanResult} — same shape as
 * Create/Update/ChangeStatus, but this is the only in-port that populates
 * its nested {@code levels}/{@code subjects}.
 */
public interface GetAcademicPlanUseCase {

	AcademicPlanResult getById(UUID id);
}
