package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1):
 * {@code {id, curp, firstName, lastName1, lastName2, institutionalEmail}}.
 */
public record PersonResponse(UUID id, String curp, String firstName, String lastName1, String lastName2,
		String institutionalEmail) {
}
