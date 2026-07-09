package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a {@code PlanLevel} to an existing {@code AcademicPlan} (spec: "Add
 * and Update Plan Level"). Enforces the {@code [1, totalLevels]} range at
 * this layer; duplicate {@code levelNumber} rejection is delegated to
 * {@link AcademicPlan#addLevel} (design.md — Decision: "Boundary enforcement
 * — no path to children except through the plan").
 */
public class AddPlanLevelUseCaseImpl implements AddPlanLevelUseCase {

	private final AcademicPlanRepository planRepository;

	public AddPlanLevelUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public PlanLevelResult addLevel(AddPlanLevelCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));
		if (command.levelNumber() < 1 || command.levelNumber() > plan.getTotalLevels()) {
			throw new IllegalArgumentException(
					"levelNumber must be within [1, totalLevels=" + plan.getTotalLevels() + "]: " + command.levelNumber());
		}

		plan.addLevel(command.levelNumber(), command.type(), command.description());
		AcademicPlan saved = planRepository.save(plan);

		/*
		 * Re-fetched from the SAVED plan by levelNumber (unique within the
		 * plan), not the in-memory reference returned by addLevel() above —
		 * apply-phase discovery: on a real JPA repository, save() on an
		 * already-managed AcademicPlan does not guarantee the original child
		 * object instance is the one carrying the generated id after the
		 * flush; searching the persisted graph by a known unique business
		 * key is the reliable way to obtain the id-bearing instance.
		 */
		PlanLevel savedLevel = saved.getLevels().stream()
				.filter(candidate -> candidate.getLevelNumber() == command.levelNumber()).findFirst().orElseThrow(
						() -> new IllegalStateException("Added level disappeared: " + command.levelNumber()));

		return CreateAcademicPlanUseCaseImpl.toLevelResult(savedLevel);
	}
}
