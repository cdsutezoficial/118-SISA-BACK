package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /payment-areas}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record PaymentAreaListResponse(List<PaymentAreaListItemResponse> items, long totalElements, int totalPages,
		int page, int size) {
}
