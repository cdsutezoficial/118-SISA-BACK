package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;

import java.util.List;
import java.util.UUID;

/**
 * Full, unpaginated pricing history for a {@code conceptId} (plan section 4
 * — low expected volume per concept, unlike the paginated listings elsewhere
 * in this module). Reuses {@link PaymentRateResult} — same shape as
 * {@code SetPaymentRateUseCase}, same convention as
 * {@code GetPaymentConceptUseCase} reusing {@code PaymentConceptResult}.
 */
public interface ListPaymentRatesUseCase {

	List<PaymentRateResult> listRates(UUID conceptId);
}
