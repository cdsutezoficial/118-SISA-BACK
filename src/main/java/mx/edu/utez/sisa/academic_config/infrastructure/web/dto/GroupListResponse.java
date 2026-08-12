package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /groups}: {@code {items[], totalElements,
 * totalPages, page, size}}.
 */
public record GroupListResponse(List<GroupListItemResponse> items, long totalElements, int totalPages, int page,
		int size) {
}
