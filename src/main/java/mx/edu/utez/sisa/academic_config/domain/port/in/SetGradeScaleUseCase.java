package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Defines a {@code GradeScale} for a plan + classification in a single
 * "set complete" operation — creates the scale and all of its entries
 * together (docs/plans/2026-07-20-grade-scale.md §1: "una sola operación de
 * reemplazo completo (escala + todos sus rangos en un solo submit)"). There
 * is deliberately no independent {@code GradeScale} controller or repository
 * — implementations depend only on {@code AcademicPlanRepository} and
 * {@code SubjectClassificationRepository}, delegating the invariant checks
 * ({@code classificationId} uniqueness within the plan, entry
 * coverage/gap/overlap) to {@code AcademicPlan.setGradeScale} (design.md —
 * Decision: "Boundary enforcement — no path to children except through the
 * plan").
 */
public interface SetGradeScaleUseCase {

	GradeScaleResult setGradeScale(SetGradeScaleCommand command);

	/**
	 * @param classificationId MUST reference an existing {@code SubjectClassification}
	 * @param numericMin       MUST be less than {@code numericMax}
	 * @param entries          MUST fully cover {@code [numericMin, numericMax]} with no
	 *                         gaps and no overlaps (PO-confirmed rule)
	 */
	record SetGradeScaleCommand(UUID planId, UUID classificationId, BigDecimal numericMin, BigDecimal numericMax,
			List<GradeScaleEntryCommand> entries) {
	}

	/**
	 * Reused by {@link UpdateGradeScaleUseCase} as the entry shape of its
	 * command, avoiding a duplicate record for the same input data.
	 */
	record GradeScaleEntryCommand(BigDecimal fromValue, BigDecimal toValue, String letter, String description,
			boolean passed) {
	}
}
