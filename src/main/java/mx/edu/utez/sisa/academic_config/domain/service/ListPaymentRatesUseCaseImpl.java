package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;

import java.util.List;
import java.util.UUID;

/**
 * Full, unpaginated pricing history for a {@code conceptId} (plan section 4).
 * Deliberately does NOT validate {@code conceptId} existence — a nonexistent
 * or wrong id simply yields an empty history, same as every other
 * query-by-filter listing in this module (e.g. {@code GET /generations?programId=}
 * with an unknown {@code programId} returns an empty page rather than 404).
 */
public class ListPaymentRatesUseCaseImpl implements ListPaymentRatesUseCase {

	private final PaymentRateRepository paymentRateRepository;

	public ListPaymentRatesUseCaseImpl(PaymentRateRepository paymentRateRepository) {
		this.paymentRateRepository = paymentRateRepository;
	}

	@Override
	public List<PaymentRateResult> listRates(UUID conceptId) {
		return paymentRateRepository.findHistoryByConceptId(conceptId).stream()
				.map(SetPaymentRateUseCaseImpl::toResult).toList();
	}
}
