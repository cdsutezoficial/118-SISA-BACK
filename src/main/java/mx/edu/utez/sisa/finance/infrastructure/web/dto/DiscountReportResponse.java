package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /finance/discounts/report} response: the discounts granted in the
 * range, echoing the bounds so the frontend can title the export.
 */
public record DiscountReportResponse(Instant from, Instant to, long count, List<BenefitDetailResponse> items) {
}
