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
 * Enforces that {@code levelNumber} is {@code >= 1} at this layer — the
 * number is a free label, not an index, so it can exceed {@code totalLevels}
 * (a 4-level plan may be numbered 7, 8, 9, 10).
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
		if (command.levelNumber() < 1) {
			throw new InvalidPlanDataException(
					"El número de nivel debe ser un entero mayor o igual a 1: " + command.levelNumber());
		}

		plan.updateLevel(command.levelId(), command.levelNumber(), command.type(),
				AcademicPlanTextNormalizer.levelDescription(command.description()));
		AcademicPlan saved = planRepository.save(plan);

		PlanLevel updatedLevel = saved.getLevels().stream().filter(level -> command.levelId().equals(level.getId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Updated level disappeared: " + command.levelId()));

		return CreateAcademicPlanUseCaseImpl.toLevelResult(updatedLevel);
	}
}
