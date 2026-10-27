package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.util.List;

/**
 * {@code GET /finance/benefits} response: the matching discounts, newest
 * first, plus the total so the screen can show "N descuentos".
 */
public record BenefitListResponse(List<BenefitDetailResponse> items, long total) {
}
