package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code PlanLevel} owned by an {@code AcademicPlan}
 * (spec: "Add and Update Plan Level"). Delegates the not-found and
 * duplicate-{@code levelNumber} checks to {@link AcademicPlan#updateLevel}.
 * Enforces the {@code [1, totalLevels]} range at this layer (post-verify
 * fast-follow bugfix — {@code AddPlanLevelUseCaseImpl} already enforced this
 * range when adding a level, but the equivalent guard was missing here,
 * silently accepting/persisting an out-of-range {@code levelNumber} on
 * update).
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
		if (command.levelNumber() < 1 || command.levelNumber() > plan.getTotalLevels()) {
			throw new InvalidPlanDataException(
					"levelNumber must be within [1, totalLevels=" + plan.getTotalLevels() + "]: " + command.levelNumber());
		}

		plan.updateLevel(command.levelId(), command.levelNumber(), command.type(), command.description());
		AcademicPlan saved = planRepository.save(plan);

		PlanLevel updatedLevel = saved.getLevels().stream().filter(level -> command.levelId().equals(level.getId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Updated level disappeared: " + command.levelId()));

		return CreateAcademicPlanUseCaseImpl.toLevelResult(updatedLevel);
	}
}
