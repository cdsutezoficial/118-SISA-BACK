package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2):
 * {@code {items[], totalElements, totalPages, page, size}}.
 */
public record PersonListResponse(List<PersonListItemResponse> items, long totalElements, int totalPages, int page,
		int size) {
}
