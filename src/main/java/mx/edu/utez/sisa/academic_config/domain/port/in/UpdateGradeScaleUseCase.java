package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase.GradeScaleEntryCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Replaces an existing {@code GradeScale}'s classification, range, and
 * entries in full (docs/plans/2026-07-20-grade-scale.md §2: "PUT ...
 * reemplaza clasificación/min/max/rangos completos"). Delegates to
 * {@code AcademicPlan.updateGradeScale}.
 */
public interface UpdateGradeScaleUseCase {

	GradeScaleResult updateGradeScale(UpdateGradeScaleCommand command);

	record UpdateGradeScaleCommand(UUID planId, UUID scaleId, UUID classificationId, BigDecimal numericMin,
			BigDecimal numericMax, List<GradeScaleEntryCommand> entries) {
	}
}
