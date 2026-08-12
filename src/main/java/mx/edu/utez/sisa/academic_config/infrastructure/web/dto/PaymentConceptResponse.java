package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET}/{@code PATCH}
 * {@code /payment-concepts} — mirrors {@code SubjectClassificationResponse}'s
 * "full post-operation state" convention.
 */
public record PaymentConceptResponse(UUID id, String name, String description, String policies,
		PaymentConceptType type, boolean isTuition, boolean isStandalone, Integer maxPerStudent,
		Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
		PaymentConceptStatus status) {
}
