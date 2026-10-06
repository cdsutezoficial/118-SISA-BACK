package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /payment-concepts}. {@code maxPerStudent},
 * {@code maxPerPeriod}, the {@code availableFrom <= availableUntil}
 * relationship, the {@code type}/{@code levelNumber} pairing and the rate-set
 * rules are deliberately NOT bean-validated here — they need cross-record state
 * and are enforced by {@code CreatePaymentConceptUseCaseImpl} via
 * {@code InvalidPaymentConceptDataException},
 * {@code DuplicatePaymentQuotaLevelException} and
 * {@code IncompletePaymentRateSetException}, same convention as
 * {@code AcademicPlan}'s {@code minPassingGrade} range (see
 * {@code CreateAcademicPlanRequest}'s Javadoc).
 *
 * <p>
 * {@code rates} travels on the create so a {@code PERIODIC_QUOTA} is never
 * persisted in the state where it exists but prices nothing. For every other
 * type it is optional and may be omitted.
 *
 * <p>No {@code programIds}: a concept's scope is stated by its rates. A client
 * still sending the old field is not an error — Jackson ignores unknown
 * properties by default here.
 */
public record CreatePaymentConceptRequest(@NotBlank String name, @NotBlank String code, String description,
		String policies, @NotNull PaymentConceptType type, Integer levelNumber, boolean isStandalone,
		Integer maxPerStudent, Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom,
		LocalDate availableUntil, UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal,
		boolean isAccumulable, boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds,
		@Valid List<PaymentRateDraftRequest> rates) {

	/**
	 * One requested rate. {@code amount} has no {@code @NotNull}: a caller that
	 * prices a recurring quota for every career is expected to send a row for
	 * each, and a null amount there is a domain error the reconciliation reports
	 * with the destination named, not a generic 400 with a field path.
	 */
	public record PaymentRateDraftRequest(UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId) {
	}
}