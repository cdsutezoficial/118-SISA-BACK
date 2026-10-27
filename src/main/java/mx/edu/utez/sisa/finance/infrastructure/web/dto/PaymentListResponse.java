package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.util.List;

/**
 * Paginated envelope for {@code GET /finance/payments}, same shape as
 * {@code PaymentAreaListResponse}.
 */
public record PaymentListResponse(List<PaymentListItemResponse> items, long totalElements, int totalPages, int page,
		int size) {
}
