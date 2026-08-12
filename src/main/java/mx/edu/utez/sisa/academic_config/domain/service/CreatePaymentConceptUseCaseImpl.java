package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Creates a {@code PaymentConcept} catalog entry (plan section 5 —
 * {@code CreatePaymentConceptUseCase}, the exact name already documented in
 * {@code 02-config-academica.md} line 299). No uniqueness check on
 * {@code name} — deliberately, per plan section 4, same criterion already
 * used in {@code CreateSubjectClassificationUseCaseImpl}. Enforces the
 * {@code maxPerStudent}/{@code maxPerPeriod} > 0 and
 * {@code availableFrom <= availableUntil} range rules (plan section 4 —
 * inferred, marked for correction).
 */
public class CreatePaymentConceptUseCaseImpl implements CreatePaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	public CreatePaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
	}

	@Override
	@Transactional
	public PaymentConceptResult createPaymentConcept(CreatePaymentConceptCommand command) {
		validate(command.maxPerStudent(), command.maxPerPeriod(), command.availableFrom(), command.availableUntil());

		PaymentConcept concept = new PaymentConcept(command.name(), command.description(), command.policies(),
				command.type(), command.isTuition(), command.isStandalone(), command.maxPerStudent(),
				command.maxPerPeriod(), command.requiresValidation(), command.availableFrom(),
				command.availableUntil());
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return toResult(saved);
	}

	/**
	 * Package-visible (not {@code private}) so
	 * {@code UpdatePaymentConceptUseCaseImpl} can reuse the same range checks
	 * without duplicating them — unlike
	 * {@code CreateAcademicPlanUseCaseImpl}/{@code UpdateAcademicPlanUseCaseImpl},
	 * which duplicate their {@code minPassingGrade} range check inline in each
	 * class.
	 */
	static void validate(Integer maxPerStudent, Integer maxPerPeriod, LocalDate availableFrom,
			LocalDate availableUntil) {
		if (maxPerStudent != null && maxPerStudent <= 0) {
			throw new InvalidPaymentConceptDataException("maxPerStudent must be greater than 0: " + maxPerStudent);
		}
		if (maxPerPeriod != null && maxPerPeriod <= 0) {
			throw new InvalidPaymentConceptDataException("maxPerPeriod must be greater than 0: " + maxPerPeriod);
		}
		if (availableFrom != null && availableUntil != null && availableFrom.isAfter(availableUntil)) {
			throw new InvalidPaymentConceptDataException(
					"availableFrom must not be after availableUntil: " + availableFrom + " > " + availableUntil);
		}
	}

	static PaymentConceptResult toResult(PaymentConcept concept) {
		return new PaymentConceptResult(concept.getId(), concept.getName(), concept.getDescription(),
				concept.getPolicies(), concept.getType(), concept.isTuition(), concept.isStandalone(),
				concept.getMaxPerStudent(), concept.getMaxPerPeriod(), concept.isRequiresValidation(),
				concept.getAvailableFrom(), concept.getAvailableUntil(), concept.getStatus());
	}
}
