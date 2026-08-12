package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a {@code PaymentConcept} catalog entry (docs:
 * {@code 02-config-academica.md} lines 234-252, plan:
 * {@code docs/plans/2026-07-28-payment-concept.md}). Role authorization
 * (ADMIN or PERSONAL_FINANZAS — NOT SERVICIOS_ESCOLARES, unlike every other
 * {@code academic_config} aggregate) is enforced by
 * {@code SecurityFilterConfig}, not here — same convention as
 * {@code CreateSubjectClassificationUseCase}.
 */
public interface CreatePaymentConceptUseCase {

	PaymentConceptResult createPaymentConcept(CreatePaymentConceptCommand command);

	/**
	 * @param name               NOT unique — this catalog has no {@code code} field and no
	 *                           documented uniqueness constraint at all (plan section 4)
	 * @param maxPerStudent      null = unlimited; when provided MUST be > 0
	 * @param maxPerPeriod       null = unlimited; when provided MUST be > 0
	 * @param availableFrom      when both this and {@code availableUntil} are provided,
	 *                           MUST NOT be after it
	 */
	record CreatePaymentConceptCommand(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
	}

	record PaymentConceptResult(UUID id, String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
			PaymentConceptStatus status) {
	}
}
