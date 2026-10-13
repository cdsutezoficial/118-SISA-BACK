package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;

import java.util.UUID;

/**
 * Adds a {@code PlanLevel} to an existing {@code AcademicPlan} (spec: "Add
 * and Update Plan Level"). There is deliberately no independent
 * {@code PlanLevel} controller or repository — implementations depend only
 * on {@code AcademicPlanRepository} and delegate the invariant checks
 * ({@code levelNumber} range and uniqueness) to
 * {@code AcademicPlan.addLevel} (design.md — Decision: "Boundary
 * enforcement — no path to children except through the plan").
 */
public interface AddPlanLevelUseCase {

	PlanLevelResult addLevel(AddPlanLevelCommand command);

	/**
	 * @param levelNumber must be {@code >= 1} and unique within the plan; it is a
	 *                    free label, so it may exceed {@code totalLevels}
	 */
	record AddPlanLevelCommand(UUID planId, int levelNumber, PlanLevelType type, String description) {
	}
}
