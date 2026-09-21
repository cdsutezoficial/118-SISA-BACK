package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

/**
 * Request body for {@code PATCH /payment-areas/{id}/status}: {@code
 * { "status": "ACTIVE" | "INACTIVE" }}.
 */
public record ChangePaymentAreaStatusRequest(@NotNull PaymentAreaStatus status) {
}
