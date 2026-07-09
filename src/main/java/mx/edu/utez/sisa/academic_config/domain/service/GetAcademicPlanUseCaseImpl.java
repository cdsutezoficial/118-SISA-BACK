package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicPlan} by id, including its full
 * {@code PlanLevel}/{@code Subject} child graph (spec: "Get Academic Plan by
 * Id").
 */
public class GetAcademicPlanUseCaseImpl implements GetAcademicPlanUseCase {

	private final AcademicPlanRepository planRepository;

	public GetAcademicPlanUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	public AcademicPlanResult getById(UUID id) {
		AcademicPlan plan = planRepository.findById(id)
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + id));
		return CreateAcademicPlanUseCaseImpl.toResult(plan);
	}
}
