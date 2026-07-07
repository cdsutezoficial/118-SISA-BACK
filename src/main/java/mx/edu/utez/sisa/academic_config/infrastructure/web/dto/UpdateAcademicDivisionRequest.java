package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request body for {@code PUT /divisions/{id}} (design.md — REST endpoints).
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /divisions/{id}/status}.
 */
public record UpdateAcademicDivisionRequest(@NotBlank String name, @NotBlank String code, String description,
		UUID directorPersonId) {
}
