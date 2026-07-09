package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;

import java.util.UUID;

/**
 * Updates an existing {@code PlanLevel} owned by an {@code AcademicPlan}
 * (spec: "Add and Update Plan Level"). Delegates to
 * {@code AcademicPlan.updateLevel}.
 */
public interface UpdatePlanLevelUseCase {

	PlanLevelResult updateLevel(UpdatePlanLevelCommand command);

	record UpdatePlanLevelCommand(UUID planId, UUID levelId, int levelNumber, PlanLevelType type,
			String description) {
	}
}
