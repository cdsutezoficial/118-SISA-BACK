package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /high-school-types/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /high-school-types/{id}/status}.
 */
public record UpdateHighSchoolTypeRequest(@NotBlank String name) {
}
