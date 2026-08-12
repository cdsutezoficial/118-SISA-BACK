package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /payment-concepts/{conceptId}/rates}: a flat
 * array, no pagination metadata (plan section 4 — full history, low expected
 * volume per concept).
 */
public record PaymentRateListResponse(List<PaymentRateResponse> items) {
}
