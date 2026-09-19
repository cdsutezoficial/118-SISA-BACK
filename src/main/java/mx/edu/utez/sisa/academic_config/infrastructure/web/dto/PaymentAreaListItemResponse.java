package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /payment-areas}.
 */
public record PaymentAreaListItemResponse(UUID id, String name, String code, String description,
		PaymentAreaStatus status) {
}
