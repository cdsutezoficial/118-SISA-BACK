package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /payment-areas/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /payment-areas/{id}/status}.
 */
public record UpdatePaymentAreaRequest(@NotBlank String name, @NotBlank String code, String description) {
}
