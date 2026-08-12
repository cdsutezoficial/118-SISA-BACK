package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;

/**
 * Request body for {@code PATCH /payment-concepts/{id}/status}:
 * {@code { "status": "ACTIVE" | "INACTIVE" }}, same shape as
 * {@code ChangeClassificationStatusRequest}.
 */
public record ChangePaymentConceptStatusRequest(@NotNull PaymentConceptStatus status) {
}
