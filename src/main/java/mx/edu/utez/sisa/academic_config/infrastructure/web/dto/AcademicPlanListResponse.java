package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /plans}: {@code {items[], totalElements,
 * totalPages, page, size}}, mirroring {@code GET /programs}.
 */
public record AcademicPlanListResponse(List<AcademicPlanListItemResponse> items, long totalElements, int totalPages,
		int page, int size) {
}
