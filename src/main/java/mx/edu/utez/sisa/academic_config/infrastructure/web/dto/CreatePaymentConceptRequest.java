package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.time.LocalDate;

/**
 * Request body for {@code POST /payment-concepts}. {@code maxPerStudent},
 * {@code maxPerPeriod} and the {@code availableFrom <= availableUntil}
 * relationship are deliberately NOT bean-validated here — they are enforced
 * by {@code CreatePaymentConceptUseCaseImpl} via
 * {@code InvalidPaymentConceptDataException}, same convention as
 * {@code AcademicPlan}'s {@code minPassingGrade} range (see
 * {@code CreateAcademicPlanRequest}'s Javadoc).
 */
public record CreatePaymentConceptRequest(@NotBlank String name, String description, String policies,
		@NotNull PaymentConceptType type, boolean isTuition, boolean isStandalone, Integer maxPerStudent,
		Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
}
