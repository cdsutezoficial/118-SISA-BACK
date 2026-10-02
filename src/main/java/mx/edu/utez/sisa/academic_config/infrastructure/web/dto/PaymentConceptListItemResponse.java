package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.util.UUID;

/**
 * A single row of {@code GET /payment-concepts}. {@code code} and
 * {@code levelNumber} are both carried because a recurring quota is only
 * distinguishable from the others by its level, and a support user looking up a
 * concept will try either identifier.
 */
public record PaymentConceptListItemResponse(UUID id, String name, String code, PaymentConceptType type,
		Integer levelNumber, boolean isStandalone, PaymentConceptStatus status) {
}