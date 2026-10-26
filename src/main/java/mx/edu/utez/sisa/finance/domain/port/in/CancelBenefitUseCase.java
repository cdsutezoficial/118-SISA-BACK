package mx.edu.utez.sisa.finance.domain.port.in;

import java.util.UUID;

/**
 * Cancels (status {@code CANCELLED}, never deletes) a discount that has not
 * been applied (Fase 2 §5.1). Cancelling affects only future quotes.
 */
public interface CancelBenefitUseCase {

	BenefitView cancelBenefit(UUID id);
}
