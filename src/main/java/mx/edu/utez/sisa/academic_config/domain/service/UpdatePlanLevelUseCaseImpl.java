package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code PlanLevel} owned by an {@code AcademicPlan}
 * (spec: "Add and Update Plan Level"). Delegates the not-found and
 * duplicate-{@code levelNumber} checks to {@link AcademicPlan#updateLevel}.
 */
public class UpdatePlanLevelUseCaseImpl implements UpdatePlanLevelUseCase {

	private final AcademicPlanRepository planRepository;

	public UpdatePlanLevelUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public PlanLevelResult updateLevel(UpdatePlanLevelCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.updateLevel(command.levelId(), command.levelNumber(), command.type(), command.description());
		AcademicPlan saved = planRepository.save(plan);

		PlanLevel updatedLevel = saved.getLevels().stream().filter(level -> command.levelId().equals(level.getId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Updated level disappeared: " + command.levelId()));

		return CreateAcademicPlanUseCaseImpl.toLevelResult(updatedLevel);
	}
}
