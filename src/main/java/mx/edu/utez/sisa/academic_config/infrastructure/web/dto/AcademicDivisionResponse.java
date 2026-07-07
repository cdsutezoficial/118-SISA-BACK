package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.UUID;

/**
 * Response body shared by {@code POST /divisions}, {@code PUT /divisions/{id}}
 * and {@code PATCH /divisions/{id}/status} — all three return the full
 * post-operation division state (mirrors
 * {@code CreateAcademicDivisionUseCase.AcademicDivisionResult}).
 */
public record AcademicDivisionResponse(UUID id, String name, String code, String description, UUID directorPersonId,
		DivisionStatus status) {
}
