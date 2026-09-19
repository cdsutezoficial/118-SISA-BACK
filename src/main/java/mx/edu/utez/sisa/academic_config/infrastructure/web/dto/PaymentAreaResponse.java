package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

import java.util.UUID;

/**
 * Response body shared by {@code POST /payment-areas},
 * {@code PUT /payment-areas/{id}}, {@code GET /payment-areas/{id}} and
 * {@code PATCH /payment-areas/{id}/status} — all return the full
 * post-operation area state (mirrors
 * {@code CreatePaymentAreaUseCase.PaymentAreaResult}).
 */
public record PaymentAreaResponse(UUID id, String name, String code, String description, PaymentAreaStatus status) {
}
