package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code PaymentConcept}'s catalog fields (plan section
 * 5). {@code status} is deliberately absent from this command — status
 * transitions are the sole responsibility of
 * {@code ChangePaymentConceptStatusUseCase}. Re-applies the same
 * {@code maxPerStudent}/{@code maxPerPeriod}/{@code availableFrom} range
 * checks as Create, via the shared
 * {@link CreatePaymentConceptUseCaseImpl#validate} helper, plus the
 * extension-field reference checks with {@code selfId} set to the concept
 * being updated (extension plan §3).
 */
public class UpdatePaymentConceptUseCaseImpl implements UpdatePaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	private final PaymentAreaRepository paymentAreaRepository;

	public UpdatePaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository,
			PaymentAreaRepository paymentAreaRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	@Transactional
	public PaymentConceptResult updatePaymentConcept(UpdatePaymentConceptCommand command) {
		PaymentConcept concept = paymentConceptRepository.findById(command.paymentConceptId())
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + command.paymentConceptId()));

		CreatePaymentConceptUseCaseImpl.validate(command.maxPerStudent(), command.maxPerPeriod(),
				command.availableFrom(), command.availableUntil(), command.cost(), command.isExternal(),
				command.costExternal(), command.quotaLimit());
		CreatePaymentConceptUseCaseImpl.validateReferences(command.areaId(), command.linkedConceptIds(),
				command.paymentConceptId(), paymentAreaRepository, paymentConceptRepository);

		concept.updateDetails(command.name(), command.description(), command.policies(), command.type(),
				command.isTuition(), command.isStandalone(), command.maxPerStudent(), command.maxPerPeriod(),
				command.requiresValidation(), command.availableFrom(), command.availableUntil(), command.areaId(),
				command.cost(), command.isExternal(), command.costExternal(), command.isAccumulable(),
				command.isMulticoncept(), command.quotaLimit(), command.linkedConceptIds());
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return CreatePaymentConceptUseCaseImpl.toResult(saved);
	}
}
