package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates an {@code AcademicPlan} catalog entry (spec: "Create Academic
 * Plan"). Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, not here — mirrors
 * {@code CreateAcademicProgramUseCase}.
 */
public interface CreateAcademicPlanUseCase {

	AcademicPlanResult createPlan(CreateAcademicPlanCommand command);

	/**
	 * @param programId               required — MUST reference an existing {@code AcademicProgram}
	 *                                (spec: "programId MUST be required")
	 * @param version                 MUST be unique within {@code programId} (not globally, spec:
	 *                                "version MUST be unique within the same programId")
	 * @param minPassingGrade         MUST be within {@code [0, 10]}
	 * @param socialServiceMinLevelId MUST be {@code null} at creation regardless of
	 *                                {@code requiresSocialService} — no {@code PlanLevel} can exist yet
	 *                                for a plan that does not exist yet (spec: "Rejects
	 *                                requiresSocialService=true with a non-null socialServiceMinLevelId
	 *                                at creation")
	 */
	record CreateAcademicPlanCommand(UUID programId, String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
	}

	/**
	 * Shared result shape reused by {@code UpdateAcademicPlanUseCase},
	 * {@code GetAcademicPlanUseCase}, {@code ListAcademicPlansUseCase}'s child
	 * ports, and {@code ChangeAcademicPlanStatusUseCase} — all return the
	 * full post-operation plan state, including its child levels/subjects
	 * (design.md — Decision: single {@code AcademicPlanResult} reused by
	 * Create/Update/Get/ChangeStatus, mirroring
	 * {@code AcademicProgramResult}). {@code levels} is empty right after
	 * creation/update/status-change (no children mutated by those
	 * operations) and populated by {@code GetAcademicPlanUseCase} (spec:
	 * "full detail ... including both levels and the subject").
	 */
	record AcademicPlanResult(UUID id, UUID programId, String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId, PlanStatus status,
			List<PlanLevelResult> levels) {
	}

	/**
	 * Reused by the child level in-ports ({@code AddPlanLevelUseCase},
	 * {@code UpdatePlanLevelUseCase}) as their return shape.
	 */
	record PlanLevelResult(UUID id, int levelNumber, PlanLevelType type, String description,
			List<SubjectResult> subjects) {
	}

	/**
	 * Reused by the child subject in-ports ({@code AddSubjectToPlanUseCase},
	 * {@code UpdateSubjectUseCase}) as their return shape.
	 */
	record SubjectResult(UUID id, String code, String name, int credits, int weeklyHours, int evaluationUnits,
			int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
	}
}
