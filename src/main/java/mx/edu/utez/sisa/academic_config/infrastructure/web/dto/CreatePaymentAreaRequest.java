package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /payment-areas}. Both {@code name} and
 * {@code code} are unique business keys.
 */
public record CreatePaymentAreaRequest(@NotBlank String name, @NotBlank String code, String description) {
}
