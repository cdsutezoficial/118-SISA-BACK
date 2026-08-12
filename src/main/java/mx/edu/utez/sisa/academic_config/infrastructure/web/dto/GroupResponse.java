package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET (by id)}/
 * {@code PATCH .../status} on {@code /groups} — mirrors
 * {@code GenerationResponse}'s "full post-operation state" convention.
 */
public record GroupResponse(UUID id, UUID generationId, UUID periodId, UUID planLevelId, UUID programId, String code,
		int maxCapacity, Shift shift, GroupStatus status) {
}
