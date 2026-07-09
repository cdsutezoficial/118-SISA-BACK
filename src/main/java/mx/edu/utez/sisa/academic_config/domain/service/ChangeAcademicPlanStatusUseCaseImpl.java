package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles an {@code AcademicPlan}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Plan"). Single
 * interactor parameterized by target status, mirroring
 * {@code ChangeAcademicProgramStatusUseCaseImpl}. Multiple {@code ACTIVE}
 * plans may coexist per {@code programId}, so this operation never consults
 * sibling plans — toggling one is independent of the others.
 */
public class ChangeAcademicPlanStatusUseCaseImpl implements ChangeAcademicPlanStatusUseCase {

	private final AcademicPlanRepository planRepository;

	public ChangeAcademicPlanStatusUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public AcademicPlanResult changeStatus(ChangeStatusCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		if (command.target() == PlanStatus.ACTIVE) {
			plan.activate();
		} else {
			plan.deactivate();
		}
		AcademicPlan saved = planRepository.save(plan);

		return CreateAcademicPlanUseCaseImpl.toResult(saved);
	}
}
