package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /subject-classifications}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record SubjectClassificationListResponse(List<SubjectClassificationListItemResponse> items,
		long totalElements, int totalPages, int page, int size) {
}
