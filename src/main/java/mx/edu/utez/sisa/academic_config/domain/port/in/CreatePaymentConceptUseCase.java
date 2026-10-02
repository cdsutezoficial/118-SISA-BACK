package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Creates a {@code PaymentConcept} catalog entry (docs:
 * {@code 02-config-academica.md} lines 234-252, plan:
 * {@code docs/plans/2026-07-28-payment-concept.md}). Role authorization
 * (ADMIN or PERSONAL_FINANZAS — NOT SERVICIOS_ESCOLARES, unlike every other
 * {@code academic_config} aggregate) is enforced by
 * {@code SecurityFilterConfig}, not here — same convention as
 * {@code CreateSubjectClassificationUseCase}.
 *
 * <p>
 * A {@code PERIODIC_QUOTA} concept cannot legally exist without pricing every
 * ACTIVE program, so its rates arrive on this command rather than through a
 * follow-up call: the alternative leaves a window where the catalog holds a
 * recurring quota with no price for some student, which is exactly the state
 * {@code IncompletePaymentRateSetException} exists to prevent.
 */
public interface CreatePaymentConceptUseCase {

	PaymentConceptResult createPaymentConcept(CreatePaymentConceptCommand command);

	/**
	 * @param code               required, unique case-insensitively — see
	 *                           {@code PaymentConcept} for why it is captured by hand
	 * @param levelNumber        required (>= 1) for {@code PERIODIC_QUOTA}, null otherwise;
	 *                           at most one ACTIVE quota concept per level
	 * @param rates              the complete rate set, applied in the same transaction. Required
	 *                           (and must cover every ACTIVE program) for {@code PERIODIC_QUOTA};
	 *                           may be empty for every other type
	 * @param maxPerStudent      null = unlimited; when provided MUST be > 0
	 * @param maxPerPeriod       null = unlimited; when provided MUST be > 0
	 * @param availableFrom      when both this and {@code availableUntil} are provided,
	 *                           MUST NOT be after it
	 */
	record CreatePaymentConceptCommand(String name, String code, String description, String policies,
			PaymentConceptType type, Integer levelNumber, boolean isStandalone, Integer maxPerStudent,
			Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
			UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds,
			List<PaymentRateDraft> rates) {
	}

	/**
	 * One requested rate, keyed by destination. {@code amount} is nullable here
	 * so the reconciliation can tell "caller did not price this" apart from
	 * "caller priced this at zero", which are different errors.
	 */
	record PaymentRateDraft(UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId) {
	}

	record PaymentConceptResult(UUID id, String name, String code, String description, String policies,
			PaymentConceptType type, Integer levelNumber, boolean isStandalone, Integer maxPerStudent,
			Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
			PaymentConceptStatus status, UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal,
			boolean isAccumulable, boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
	}
}