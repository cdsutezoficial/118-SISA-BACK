package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1).
 * {@code institutionalEmail} is required here even though the {@code Person}
 * entity column itself allows null — bean-validated at this DTO boundary,
 * not on the entity, same split the plan calls for ("obligatorio en este
 * endpoint"). {@code lastName2} is the only optional field.
 */
public record CreatePersonRequest(@NotBlank String curp, @NotBlank String firstName, @NotBlank String lastName1,
		String lastName2, @NotBlank String institutionalEmail) {
}
