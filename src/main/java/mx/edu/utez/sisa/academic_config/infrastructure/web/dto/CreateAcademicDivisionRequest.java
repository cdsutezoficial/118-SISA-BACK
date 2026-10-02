package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /divisions} (design.md — REST endpoints).
 * {@code directorPersonId} is optional (spec: "creation MUST still succeed"
 * without a director).
 */
public record CreateAcademicDivisionRequest(
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre contiene caracteres no válidos.") String name,
		@NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "La clave solo puede contener letras, números y guiones.") String code,
		@Size(max = 500) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description,
		UUID directorPersonId) {
}
