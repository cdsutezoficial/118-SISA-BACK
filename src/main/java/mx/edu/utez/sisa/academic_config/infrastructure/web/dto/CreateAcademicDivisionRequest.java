package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request body for {@code POST /divisions} (design.md — REST endpoints).
 * {@code directorPersonId} is optional (spec: "creation MUST still succeed"
 * without a director).
 */
public record CreateAcademicDivisionRequest(@NotBlank String name, @NotBlank String code, String description,
		UUID directorPersonId) {
}
