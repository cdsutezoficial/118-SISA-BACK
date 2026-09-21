package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentAreaDataException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code PaymentArea} catalog entry. Enforces name/code uniqueness
 * against {@link PaymentAreaRepository} (case-insensitive at the adapter) —
 * same shape as {@code CreateAcademicDivisionUseCaseImpl}.
 */
public class CreatePaymentAreaUseCaseImpl implements CreatePaymentAreaUseCase {

	private final PaymentAreaRepository paymentAreaRepository;

	public CreatePaymentAreaUseCaseImpl(PaymentAreaRepository paymentAreaRepository) {
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	@Transactional
	public PaymentAreaResult createPaymentArea(CreatePaymentAreaCommand command) {
		validate(command.name(), command.code());

		if (paymentAreaRepository.findByName(command.name()).isPresent()) {
			throw new DuplicatePaymentAreaNameException("Payment area name already in use: " + command.name());
		}
		if (paymentAreaRepository.findByCode(command.code()).isPresent()) {
			throw new DuplicatePaymentAreaCodeException("Payment area code already in use: " + command.code());
		}

		PaymentArea area = new PaymentArea(command.name(), command.code(), command.description());
		PaymentArea saved = paymentAreaRepository.save(area);

		return toResult(saved);
	}

	/**
	 * Package-visible so {@code UpdatePaymentAreaUseCaseImpl} can reuse the
	 * same checks without duplicating them — same approach as
	 * {@code CreatePaymentConceptUseCaseImpl#validate}.
	 */
	static void validate(String name, String code) {
		if (name == null || name.isBlank()) {
			throw new InvalidPaymentAreaDataException("name must not be blank");
		}
		if (code == null || code.isBlank()) {
			throw new InvalidPaymentAreaDataException("code must not be blank");
		}
	}

	static PaymentAreaResult toResult(PaymentArea area) {
		return new PaymentAreaResult(area.getId(), area.getName(), area.getCode(), area.getDescription(),
				area.getStatus());
	}
}
