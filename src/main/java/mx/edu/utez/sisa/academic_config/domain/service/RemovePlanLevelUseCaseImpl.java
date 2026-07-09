package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a {@code PlanLevel} owned by an {@code AcademicPlan} (spec: "Add
 * and Update Plan Level"). Delegates the not-found, has-subjects, and
 * in-use-as-social-service-level checks to {@link AcademicPlan#removeLevel}.
 */
public class RemovePlanLevelUseCaseImpl implements RemovePlanLevelUseCase {

	private final AcademicPlanRepository planRepository;

	public RemovePlanLevelUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public void removeLevel(RemovePlanLevelCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.removeLevel(command.levelId());
		planRepository.save(plan);
	}
}
