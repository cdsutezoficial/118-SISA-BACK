package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /high-school-types/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /high-school-types/{id}/status}.
 *
 * <p>Mismo contrato que {@link CreateHighSchoolTypeRequest}, con la misma
 * justificación: la normalización y la unicidad se comprueban en el caso de uso, y
 * el DTO sólo rechaza lo que ni siquiera tiene sentido normalizar.
 */
public record UpdateHighSchoolTypeRequest(
		@NotBlank(message = "El nombre del tipo es obligatorio.") @Size(max = 150, message = "El nombre del tipo no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del tipo contiene caracteres no válidos.") String name) {
}
