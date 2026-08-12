package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET (by id)}/
 * {@code PATCH .../status} on {@code /program-admission-configs} — mirrors
 * {@code GenerationResponse}'s "full post-operation state" convention.
 */
public record ProgramAdmissionConfigResponse(UUID id, UUID programId, UUID periodId, UUID targetGenerationId,
		boolean isOffered, int maxCandidates, Instant opensAt, Instant closesAt, ProgramAdmissionConfigStatus status,
		SelectionStatus selectionStatus) {
}
