package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * A single row of {@code GET /groups}.
 */
public record GroupListItemResponse(UUID id, UUID generationId, UUID periodId, UUID planLevelId, UUID programId,
		String code, int maxCapacity, Shift shift, GroupStatus status) {
}
