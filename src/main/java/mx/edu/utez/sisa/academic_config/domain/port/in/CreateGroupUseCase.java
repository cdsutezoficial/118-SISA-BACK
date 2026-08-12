package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Creates a {@code Group} (plan: {@code docs/plans/2026-07-20-generation-group.md},
 * "Group — diseño técnico resuelto (2026-07-23)"). Role authorization (ADMIN
 * or SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not
 * here — same convention as {@code CreateGenerationUseCase}. {@code programId}
 * is NEVER accepted from the caller — it is resolved server-side from
 * {@code generationId}'s owning {@code Generation.programId}. Always
 * defaults {@code status} to {@link GroupStatus#OPEN}.
 */
public interface CreateGroupUseCase {

	GroupResult createGroup(CreateGroupCommand command);

	/**
	 * @param generationId required — MUST reference an existing {@code Generation}
	 * @param periodId     required — MUST reference an existing {@code AcademicPeriod}
	 * @param planLevelId  required — MUST belong to the {@code AcademicPlan} that {@code generationId}'s
	 *                     generation was opened against
	 * @param code         e.g. "3A" — no uniqueness rule in this slice
	 */
	record CreateGroupCommand(UUID generationId, UUID periodId, UUID planLevelId, String code, int maxCapacity,
			Shift shift) {
	}

	record GroupResult(UUID id, UUID generationId, UUID periodId, UUID planLevelId, UUID programId, String code,
			int maxCapacity, Shift shift, GroupStatus status) {
	}
}
