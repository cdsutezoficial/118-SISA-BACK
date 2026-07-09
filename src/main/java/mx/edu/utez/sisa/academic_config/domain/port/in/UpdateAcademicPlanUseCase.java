package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Updates an existing {@code AcademicPlan}'s catalog fields (spec: "Update
 * Academic Plan"). The same {@code version}-uniqueness-within-{@code programId}
 * rule from {@code CreateAcademicPlanUseCase} applies, except a plan's own
 * current value never counts as a conflict against itself.
 * {@code programId} is deliberately absent from this command — moving a
 * plan to a different program is not supported (spec: "programId MUST NOT
 * be changeable via update"), mirrored here the same way
 * {@code ChangeAcademicProgramStatusUseCase}'s command omits catalog fields:
 * the port simply gives no way to change it. {@code status} is likewise
 * absent — status transitions are the sole responsibility of
 * {@code ChangeAcademicPlanStatusUseCase}.
 */
public interface UpdateAcademicPlanUseCase {

	AcademicPlanResult updatePlan(UpdateAcademicPlanCommand command);

	/**
	 * @param socialServiceMinLevelId when {@code requiresSocialService} is {@code true}, MUST reference
	 *                                an existing {@code PlanLevel} that belongs to this same
	 *                                {@code AcademicPlan} (spec: "cross-aggregate leakage ... MUST be
	 *                                rejected")
	 */
	record UpdateAcademicPlanCommand(UUID planId, String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
	}
}
