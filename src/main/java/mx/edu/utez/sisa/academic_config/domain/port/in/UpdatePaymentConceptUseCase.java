package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Updates an existing {@code PaymentConcept}'s catalog fields (does NOT touch
 * status — same convention as every other aggregate's Update use case in
 * this module, e.g. {@code UpdateSubjectClassificationUseCase}). Re-applies
 * the same {@code code} uniqueness, {@code levelNumber} pairing and
 * {@code maxPerStudent}/{@code maxPerPeriod}/{@code availableFrom} range
 * validations as Create.
 *
 * <p>
 * Prices are NOT part of this command. They are reconciled through
 * {@code ReconcilePaymentRatesUseCase}, so editing a concept's description can
 * never be the thing that changes what a program is charged — a mistake that a
 * combined endpoint makes easy and that a separate one makes impossible.
 */
public interface UpdatePaymentConceptUseCase {

	PaymentConceptResult updatePaymentConcept(UpdatePaymentConceptCommand command);

	record UpdatePaymentConceptCommand(UUID paymentConceptId, String name, String code, String description,
			String policies, PaymentConceptType type, Integer levelNumber, boolean isStandalone, Integer maxPerStudent,
			Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil,
			UUID areaId, BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
	}
}