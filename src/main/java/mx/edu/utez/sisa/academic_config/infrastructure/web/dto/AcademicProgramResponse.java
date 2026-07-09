package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Response body shared by {@code POST /programs}, {@code PUT /programs/{id}},
 * {@code GET /programs/{id}} and {@code PATCH /programs/{id}/status} — all
 * four return the full post-operation program state (mirrors
 * {@code CreateAcademicProgramUseCase.AcademicProgramResult}).
 */
public record AcademicProgramResponse(UUID id, UUID divisionId, String name, String offerName, String code,
		AcademicLevel level, ProgramModality modality, UUID continuityProgramId, String description,
		String dgpCode, ProgramStatus status) {
}
