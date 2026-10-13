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
		// La validación va primero, sobre los valores crudos: es lo que detecta el
		// null, y los normalizadores no aceptan null (`collapse` haría NPE). Un
		// `"   "` también cae aquí, antes de que el trim lo convierta en "".
		validate(command.name(), command.code());

		// Normalizar ANTES de buscar duplicados y antes de guardar (Fase 11). Si se
		// guardara el valor crudo y sólo se normalizara la búsqueda, el índice único
		// compararía " Colegiaturas " contra "Colegiaturas" sin encontrar choque —
		// la colación es NO PAD — y el alta reventaría después con un
		// DuplicateKeyException sin manejar, que el usuario vería como un 500.
		String name = PaymentAreaTextNormalizer.name(command.name());
		String code = PaymentAreaTextNormalizer.code(command.code());
		String description = PaymentAreaTextNormalizer.description(command.description());

		if (paymentAreaRepository.findByName(name).isPresent()) {
			throw new DuplicatePaymentAreaNameException("Payment area name already in use: " + name);
		}
		if (paymentAreaRepository.findByCode(code).isPresent()) {
			throw new DuplicatePaymentAreaCodeException("Payment area code already in use: " + code);
		}

		PaymentArea area = new PaymentArea(name, code, description);
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
