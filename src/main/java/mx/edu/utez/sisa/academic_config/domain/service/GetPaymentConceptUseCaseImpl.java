package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code PaymentConcept} by id, 404 if missing — same
 * pattern as {@code GetSubjectClassificationUseCaseImpl}.
 */
public class GetPaymentConceptUseCaseImpl implements GetPaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	public GetPaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
	}

	@Override
	public PaymentConceptResult getById(UUID id) {
		PaymentConcept concept = paymentConceptRepository.findById(id)
				.orElseThrow(() -> new PaymentConceptNotFoundException("Payment concept not found: " + id));
		return CreatePaymentConceptUseCaseImpl.toResult(concept);
	}
}
