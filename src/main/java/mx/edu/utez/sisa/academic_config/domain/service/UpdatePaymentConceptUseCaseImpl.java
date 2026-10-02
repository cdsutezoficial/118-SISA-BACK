package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
 *
 * <p>
 * Prices are NOT reachable from here. That is the point of the split: editing a
 * concept's description or dates cannot be the operation that changes what a
 * career is charged, and {@code ReconcilePaymentRatesUseCase} owns that
 * exclusively. Prices are nevertheless READ here, because {@code type} is one of
 * the editable fields and the legality of {@code PERIODIC_QUOTA} depends on the
 * rates — see {@link #requireQuotaCoverageSurvivesUpdate}. Read-only, never
 * written: the guard can only refuse the update, never quietly price the
 * concept as a side effect of editing its description.
 */
public class UpdatePaymentConceptUseCaseImpl implements UpdatePaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	private final PaymentAreaRepository paymentAreaRepository;

	private final PaymentRateRepository paymentRateRepository;

	private final PaymentQuotaCoverageChecker coverageChecker;

	public UpdatePaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository,
			PaymentAreaRepository paymentAreaRepository, PaymentRateRepository paymentRateRepository,
			PaymentQuotaCoverageChecker coverageChecker) {
		this.paymentConceptRepository = paymentConceptRepository;
		this.paymentAreaRepository = paymentAreaRepository;
		this.paymentRateRepository = paymentRateRepository;
		this.coverageChecker = coverageChecker;
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
		CreatePaymentConceptUseCaseImpl.validateCode(command.code(), command.paymentConceptId(),
				paymentConceptRepository);
		CreatePaymentConceptUseCaseImpl.validateQuotaLevel(command.type(), command.levelNumber(),
				command.paymentConceptId(), paymentConceptRepository);
		requireQuotaCoverageSurvivesUpdate(concept, command.type());

		concept.updateDetails(command.name(), command.code(), command.description(), command.policies(),
				command.type(), command.levelNumber(), command.isStandalone(), command.maxPerStudent(),
				command.maxPerPeriod(), command.requiresValidation(), command.availableFrom(),
				command.availableUntil(), command.areaId(), command.cost(), command.isExternal(),
				command.costExternal(), command.isAccumulable(), command.isMulticoncept(), command.quotaLimit(),
				command.linkedConceptIds());
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return CreatePaymentConceptUseCaseImpl.toResult(saved);
	}

	/**
	 * A retype that lands on {@code PERIODIC_QUOTA} has to leave the concept
	 * priced, and this is the only place that can notice.
	 *
	 * <p>
	 * The field checks above are happy with the retype on their own — a level
	 * number is present, the code is free, no duplicate quota holds the level —
	 * and the concept may well be {@code ACTIVE} already, in which case
	 * {@code ChangePaymentConceptStatusUseCase} will not revalidate anything
	 * later because there is no activation to validate. The result would be a
	 * live quota concept charging nothing to every student of that level, and the
	 * only symptom would be a zero amount at checkout.
	 *
	 * <p>
	 * Checked for both {@code ACTIVE} and {@code INACTIVE} concepts on purpose. An
	 * incomplete quota parked as {@code INACTIVE} is exactly the state that would
	 * fail on activation later, and refusing it now moves the failure to the edit
	 * that caused it instead of to whoever later clicks activate.
	 */
	private void requireQuotaCoverageSurvivesUpdate(PaymentConcept concept, PaymentConceptType newType) {
		if (newType != PaymentConceptType.PERIODIC_QUOTA
				|| concept.getType() == PaymentConceptType.PERIODIC_QUOTA) {
			return;
		}

		Set<UUID> priced = paymentRateRepository.findActiveByConceptId(concept.getId()).stream()
				.map(PaymentRate::getProgramId).filter(Objects::nonNull).collect(Collectors.toSet());

		coverageChecker.requireCompleteCoverage(priced);
	}
}
