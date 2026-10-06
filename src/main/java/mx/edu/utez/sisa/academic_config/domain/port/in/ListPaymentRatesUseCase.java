package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.PaymentRateResult;

import java.util.List;
import java.util.UUID;

/**
 * Full, unpaginated pricing history for a {@code conceptId} — low expected
 * volume per concept, unlike the paginated listings elsewhere in this module.
 * Returns both the ACTIVE row and every superseded one, so a caller can show
 * what a price was before and when it changed. Reuses
 * {@link PaymentRateResult} — same convention as {@code GetPaymentConceptUseCase}
 * reusing {@code PaymentConceptResult}.
 */
public interface ListPaymentRatesUseCase {

	List<PaymentRateResult> listRates(UUID conceptId);
}