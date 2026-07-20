package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.util.UUID;

/**
 * Removes a {@code GradeScale} owned by an {@code AcademicPlan} (its entries
 * go with it via cascade). Delegates to {@code AcademicPlan.removeGradeScale}.
 * Unlike the root {@code AcademicPlan} (never hard-deleted), child entities
 * support real deletion — same convention as {@code RemovePlanLevelUseCase}.
 */
public interface RemoveGradeScaleUseCase {

	void removeGradeScale(RemoveGradeScaleCommand command);

	record RemoveGradeScaleCommand(UUID planId, UUID scaleId) {
	}
}
