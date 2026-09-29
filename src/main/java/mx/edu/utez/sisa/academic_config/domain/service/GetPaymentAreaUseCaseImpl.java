package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code PaymentArea} by id, 404 if missing — same pattern as
 * {@code GetPaymentConceptUseCaseImpl}.
 */
public class GetPaymentAreaUseCaseImpl implements GetPaymentAreaUseCase {

	private final PaymentAreaRepository paymentAreaRepository;

	public GetPaymentAreaUseCaseImpl(PaymentAreaRepository paymentAreaRepository) {
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	public PaymentAreaResult getById(UUID id) {
		PaymentArea area = paymentAreaRepository.findById(id)
				.orElseThrow(() -> new PaymentAreaNotFoundException("Payment area not found: " + id));
		return CreatePaymentAreaUseCaseImpl.toResult(area);
	}
}
