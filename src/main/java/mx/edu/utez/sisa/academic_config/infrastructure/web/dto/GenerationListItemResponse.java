package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /generations}.
 */
public record GenerationListItemResponse(UUID id, UUID planId, UUID startPeriodId, UUID programId, int number,
		String code, GenerationStatus status) {
}
