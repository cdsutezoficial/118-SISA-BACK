package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * A single row of {@code GET /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2):
 * {@code {id, curp, firstName, lastName1, lastName2, institutionalEmail, hasUser}}.
 */
public record PersonListItemResponse(UUID id, String curp, String firstName, String lastName1, String lastName2,
		String institutionalEmail, boolean hasUser) {
}
