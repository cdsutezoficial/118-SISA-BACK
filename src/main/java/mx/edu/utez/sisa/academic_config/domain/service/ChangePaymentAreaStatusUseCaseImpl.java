package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code PaymentArea}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangePaymentConceptStatusUseCaseImpl}.
 */
public class ChangePaymentAreaStatusUseCaseImpl implements ChangePaymentAreaStatusUseCase {

	private final PaymentAreaRepository paymentAreaRepository;

	public ChangePaymentAreaStatusUseCaseImpl(PaymentAreaRepository paymentAreaRepository) {
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	@Transactional
	public PaymentAreaResult changeStatus(ChangeStatusCommand command) {
		PaymentArea area = paymentAreaRepository.findById(command.paymentAreaId())
				.orElseThrow(() -> new PaymentAreaNotFoundException(
						"Payment area not found: " + command.paymentAreaId()));

		if (command.target() == PaymentAreaStatus.ACTIVE) {
			area.activate();
		}
		else {
			area.deactivate();
		}
		PaymentArea saved = paymentAreaRepository.save(area);

		return CreatePaymentAreaUseCaseImpl.toResult(saved);
	}
}
