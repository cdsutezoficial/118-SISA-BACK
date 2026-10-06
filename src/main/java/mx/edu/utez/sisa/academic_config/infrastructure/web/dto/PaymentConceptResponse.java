package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET}/{@code PATCH}
 * {@code /payment-concepts} — mirrors {@code SubjectClassificationResponse}'s
 * "full post-operation state" convention.
 *
 * <p>No {@code programIds}. Which programs a concept charges for is answered by
 * {@code GET /payment-concepts/{id}/rates}, which is the same data the price is
 * computed from — returning a second, differently-maintained copy here is what
 * let the two disagree.
 */
public record PaymentConceptResponse(UUID id, String name, String code, String description, String policies,
		PaymentConceptType type, Integer levelNumber, boolean isStandalone, Integer maxPerStudent,
		Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
		PaymentConceptStatus status, UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal,
		boolean isAccumulable, boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
}