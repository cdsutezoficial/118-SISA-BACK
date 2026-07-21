package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET (by id)}/
 * {@code PATCH .../status} on {@code /generations} — mirrors
 * {@code AcademicPeriodResponse}'s "full post-operation state" convention.
 */
public record GenerationResponse(UUID id, UUID planId, UUID startPeriodId, UUID programId, int number, String code,
		GenerationStatus status) {
}
