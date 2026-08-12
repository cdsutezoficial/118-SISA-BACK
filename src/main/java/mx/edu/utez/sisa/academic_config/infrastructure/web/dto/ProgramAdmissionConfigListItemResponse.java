package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row of {@code GET /program-admission-configs}.
 */
public record ProgramAdmissionConfigListItemResponse(UUID id, UUID programId, UUID periodId, UUID targetGenerationId,
		boolean isOffered, int maxCandidates, Instant opensAt, Instant closesAt, ProgramAdmissionConfigStatus status,
		SelectionStatus selectionStatus) {
}
