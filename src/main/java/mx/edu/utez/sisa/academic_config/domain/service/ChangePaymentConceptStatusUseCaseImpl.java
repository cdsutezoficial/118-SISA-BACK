package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code PaymentConcept}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeSubjectClassificationStatusUseCaseImpl}.
 */
public class ChangePaymentConceptStatusUseCaseImpl implements ChangePaymentConceptStatusUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	public ChangePaymentConceptStatusUseCaseImpl(PaymentConceptRepository paymentConceptRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
	}

	@Override
	@Transactional
	public PaymentConceptResult changeStatus(ChangeStatusCommand command) {
		PaymentConcept concept = paymentConceptRepository.findById(command.paymentConceptId())
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + command.paymentConceptId()));

		if (command.target() == PaymentConceptStatus.ACTIVE) {
			concept.activate();
		}
		else {
			concept.deactivate();
		}
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return CreatePaymentConceptUseCaseImpl.toResult(saved);
	}
}
