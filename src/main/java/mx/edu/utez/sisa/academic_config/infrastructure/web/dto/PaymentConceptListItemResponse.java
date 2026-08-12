package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.util.UUID;

/**
 * A single row of {@code GET /payment-concepts}.
 */
public record PaymentConceptListItemResponse(UUID id, String name, PaymentConceptType type, boolean isTuition,
		boolean isStandalone, PaymentConceptStatus status) {
}
