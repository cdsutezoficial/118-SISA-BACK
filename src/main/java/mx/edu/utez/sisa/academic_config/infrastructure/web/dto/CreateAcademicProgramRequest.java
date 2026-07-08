package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Request body for {@code POST /programs} (design.md — REST endpoints).
 * {@code divisionId} is required (spec: "divisionId MUST be required" — it
 * MUST NOT be omitted or null), unlike
 * {@code CreateAcademicDivisionRequest.directorPersonId}. {@code
 * continuityProgramId} is optional — schema-only in this change, no
 * validation or linking logic applied.
 */
public record CreateAcademicProgramRequest(@NotNull UUID divisionId, @NotBlank String name,
		@NotBlank String offerName, @NotBlank String code, @NotNull AcademicLevel level,
		@NotNull ProgramModality modality, UUID continuityProgramId, String description) {
}
