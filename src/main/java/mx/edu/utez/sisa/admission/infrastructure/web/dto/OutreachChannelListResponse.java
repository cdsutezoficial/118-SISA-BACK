package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /outreach-channels}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record OutreachChannelListResponse(List<OutreachChannelListItemResponse> items, long totalElements,
		int totalPages, int page, int size) {
}
