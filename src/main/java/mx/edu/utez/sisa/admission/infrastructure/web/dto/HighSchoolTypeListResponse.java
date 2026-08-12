package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /high-school-types}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record HighSchoolTypeListResponse(List<HighSchoolTypeListItemResponse> items, long totalElements,
		int totalPages, int page, int size) {
}
