package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /payment-concepts/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /payment-concepts/{id}/status}.
 */
public record UpdatePaymentConceptRequest(@NotBlank String name, String description, String policies,
		@NotNull PaymentConceptType type, boolean isTuition, boolean isStandalone, Integer maxPerStudent,
		Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
		UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
		boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds, List<UUID> programIds) {
}
