package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /programs}: {@code {items[], totalElements,
 * totalPages, page, size}}.
 */
public record AcademicProgramListResponse(List<AcademicProgramListItemResponse> items, long totalElements,
		int totalPages, int page, int size) {
}
