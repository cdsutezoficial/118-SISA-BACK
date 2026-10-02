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
 *
 * <p>No {@code rates} either. Prices are reconciled through
 * {@code PUT /payment-concepts/{id}/rates}, so this body cannot change what a
 * career is charged however it is composed.
 *
 * <p>No {@code programIds}, for the same reason as
 * {@code CreatePaymentConceptRequest}: scope lives in the rates, so editing the
 * concept cannot orphan the programs it used to claim to apply to.
 */
public record UpdatePaymentConceptRequest(@NotBlank String name, @NotBlank String code, String description,
		String policies, @NotNull PaymentConceptType type, Integer levelNumber, boolean isStandalone,
		Integer maxPerStudent, Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom,
		LocalDate availableUntil, UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal,
		boolean isAccumulable, boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
}