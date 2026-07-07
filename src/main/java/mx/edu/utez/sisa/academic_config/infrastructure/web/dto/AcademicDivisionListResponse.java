package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /divisions}: {@code {items[], totalElements,
 * totalPages, page, size}}.
 */
public record AcademicDivisionListResponse(List<AcademicDivisionListItemResponse> items, long totalElements,
		int totalPages, int page, int size) {
}
