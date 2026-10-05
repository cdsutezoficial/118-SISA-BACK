package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /candidates}:
 * {@code {items[], totalElements, totalPages, page, size}} — the same envelope
 * as {@code GET /persons} and every other paginated list endpoint.
 */
public record CandidateListResponse(List<CandidateListItemResponse> items, long totalElements, int totalPages,
		int page, int size) {
}
