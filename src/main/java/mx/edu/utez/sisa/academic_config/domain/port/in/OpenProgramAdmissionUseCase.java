package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a {@code ProgramAdmissionConfig} — the exact name already
 * documented in {@code 02-config-academica.md} line 301 ("Habilita un
 * programa para un periodo de admisión"). Role authorization (ADMIN or
 * SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not here.
 * Always defaults {@code status} to {@link ProgramAdmissionConfigStatus#OPEN}
 * and {@code selectionStatus} to {@link SelectionStatus#IN_REVIEW} — neither
 * is accepted as caller input (plan: {@code docs/plans/2026-07-28-program-admission-config.md}
 * §5).
 */
public interface OpenProgramAdmissionUseCase {

	ProgramAdmissionConfigResult openProgramAdmission(OpenProgramAdmissionCommand command);

	/**
	 * @param programId          required — MUST reference an existing {@code AcademicProgram}
	 * @param periodId           required — MUST reference an existing {@code AcademicPeriod} (the
	 *                           DESTINATION period)
	 * @param targetGenerationId required — MUST reference an existing {@code Generation}
	 * @param isOffered          caller-supplied "ofertada" flag
	 * @param maxCandidates      MUST be greater than 0
	 * @param opensAt            ticket-sales window opening instant
	 * @param closesAt           MUST be strictly after {@code opensAt}
	 */
	record OpenProgramAdmissionCommand(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
	}

	record ProgramAdmissionConfigResult(UUID id, UUID programId, UUID periodId, UUID targetGenerationId,
			boolean isOffered, int maxCandidates, Instant opensAt, Instant closesAt,
			ProgramAdmissionConfigStatus status, SelectionStatus selectionStatus) {
	}
}
