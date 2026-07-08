package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Request body for {@code PUT /programs/{id}} (design.md — REST endpoints).
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /programs/{id}/status}.
 */
public record UpdateAcademicProgramRequest(@NotNull UUID divisionId, @NotBlank String name,
		@NotBlank String offerName, @NotBlank String code, @NotNull AcademicLevel level,
		@NotNull ProgramModality modality, UUID continuityProgramId, String description) {
}
