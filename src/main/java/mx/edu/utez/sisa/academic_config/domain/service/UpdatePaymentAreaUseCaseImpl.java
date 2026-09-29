package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code PaymentArea}'s catalog fields. {@code status} is
 * deliberately absent from this command — status transitions are the sole
 * responsibility of {@code ChangePaymentAreaStatusUseCase}. Applies the same
 * uniqueness rules as creation, except an area's own current
 * {@code name}/{@code code} never counts as a conflict against itself.
 */
public class UpdatePaymentAreaUseCaseImpl implements UpdatePaymentAreaUseCase {

	private final PaymentAreaRepository paymentAreaRepository;

	public UpdatePaymentAreaUseCaseImpl(PaymentAreaRepository paymentAreaRepository) {
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	@Transactional
	public PaymentAreaResult updatePaymentArea(UpdatePaymentAreaCommand command) {
		PaymentArea area = paymentAreaRepository.findById(command.paymentAreaId())
				.orElseThrow(() -> new PaymentAreaNotFoundException(
						"Payment area not found: " + command.paymentAreaId()));

		CreatePaymentAreaUseCaseImpl.validate(command.name(), command.code());

		paymentAreaRepository.findByName(command.name())
				.filter(found -> !found.getId().equals(area.getId())).ifPresent(found -> {
					throw new DuplicatePaymentAreaNameException(
							"Payment area name already in use: " + command.name());
				});
		paymentAreaRepository.findByCode(command.code())
				.filter(found -> !found.getId().equals(area.getId())).ifPresent(found -> {
					throw new DuplicatePaymentAreaCodeException(
							"Payment area code already in use: " + command.code());
				});

		area.updateDetails(command.name(), command.code(), command.description());
		PaymentArea saved = paymentAreaRepository.save(area);

		return CreatePaymentAreaUseCaseImpl.toResult(saved);
	}
}
