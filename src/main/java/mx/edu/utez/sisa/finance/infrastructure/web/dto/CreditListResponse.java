package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.util.List;

/**
 * {@code GET /finance/credits} response. The screen sums the {@code AVAILABLE}
 * rows for the "saldo a favor disponible" figure.
 */
public record CreditListResponse(List<CreditResponse> items, long total) {
}
