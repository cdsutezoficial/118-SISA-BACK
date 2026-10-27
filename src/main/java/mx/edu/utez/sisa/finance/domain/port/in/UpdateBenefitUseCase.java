package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Edits an {@code ACTIVE}, not-yet-applied discount (Fase 2 §5.1). The subject
 * ({@code personId}/{@code conceptCode}) is immutable; only the mode, window,
 * anchors and reason can change. Editing a discount referenced by a payment
 * is refused — a settled amount is never silently rewritten.
 */
public interface UpdateBenefitUseCase {

	BenefitView updateBenefit(UpdateBenefitCommand command);

	record UpdateBenefitCommand(UUID id, UUID periodId, UUID generationId, BenefitMode mode, BigDecimal percent,
			BigDecimal amount, LocalDate validFrom, LocalDate validTo, String reason) {
	}
}
