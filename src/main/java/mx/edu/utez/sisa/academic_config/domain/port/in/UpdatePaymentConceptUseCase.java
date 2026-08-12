package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Updates an existing {@code PaymentConcept}'s catalog fields (does NOT touch
 * status — same convention as every other aggregate's Update use case in
 * this module, e.g. {@code UpdateSubjectClassificationUseCase}). Re-applies
 * the same {@code maxPerStudent}/{@code maxPerPeriod}/{@code availableFrom}
 * range validations as Create.
 */
public interface UpdatePaymentConceptUseCase {

	PaymentConceptResult updatePaymentConcept(UpdatePaymentConceptCommand command);

	record UpdatePaymentConceptCommand(UUID paymentConceptId, String name, String description, String policies,
			PaymentConceptType type, boolean isTuition, boolean isStandalone, Integer maxPerStudent,
			Integer maxPerPeriod, boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
	}
}
