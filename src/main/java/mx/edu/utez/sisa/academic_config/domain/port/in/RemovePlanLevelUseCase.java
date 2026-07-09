package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.util.UUID;

/**
 * Removes a {@code PlanLevel} owned by an {@code AcademicPlan} (spec: "Add
 * and Update Plan Level"). Delegates to {@code AcademicPlan.removeLevel},
 * which rejects removal when the level still has {@code Subject} children or
 * is referenced by {@code socialServiceMinLevelId}. Unlike the root
 * {@code AcademicPlan} (never hard-deleted), child entities support real
 * deletion — they aren't independently auditable resources.
 */
public interface RemovePlanLevelUseCase {

	void removeLevel(RemovePlanLevelCommand command);

	record RemovePlanLevelCommand(UUID planId, UUID levelId) {
	}
}
