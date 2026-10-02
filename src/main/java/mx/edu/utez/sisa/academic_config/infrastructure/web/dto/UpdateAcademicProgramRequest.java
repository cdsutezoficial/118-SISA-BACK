package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Request body for {@code PUT /programs/{id}} (design.md — REST endpoints).
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /programs/{id}/status}. {@code dgpCode} is optional — MAY be
 * null or omitted when the program has not yet been registered with DGP.
 */
public record UpdateAcademicProgramRequest(@NotNull UUID divisionId,
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre contiene caracteres no válidos.") String name,
		@NotBlank @Size(max = 200) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre de oferta contiene caracteres no válidos.") String offerName,
		@NotBlank @Size(min = 2, max = 41) @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "La clave solo puede contener letras, números y guiones.") String code,
		@NotNull AcademicLevel level, @NotNull ProgramModality modality, UUID continuityProgramId,
		@Size(max = 500) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description,
		String dgpCode) {
}
