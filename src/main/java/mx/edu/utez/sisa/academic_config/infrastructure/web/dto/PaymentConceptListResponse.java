package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /payment-concepts}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record PaymentConceptListResponse(List<PaymentConceptListItemResponse> items, long totalElements,
		int totalPages, int page, int size) {
}
