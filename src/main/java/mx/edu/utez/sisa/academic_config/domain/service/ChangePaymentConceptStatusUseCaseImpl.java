package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Toggles a {@code PaymentConcept}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeSubjectClassificationStatusUseCaseImpl}.
 *
 * <p>
 * Activation is where the two {@code PERIODIC_QUOTA} invariants can be broken
 * without any other endpoint being involved, so they are re-checked here rather
 * than assumed:
 * <ul>
 * <li>one ACTIVE quota per {@code levelNumber} — a deactivation is the only way
 * to free a level, so the level is guaranteed free at deactivate time and can be
 * taken by another concept before this one comes back;
 * <li>a quota cannot be ACTIVE with an incomplete rate set — a career created
 * while this concept was inactive has no price, and reactivating would put a
 * student of that level under a quota concept that prices nothing for their
 * career.
 * </ul>
 * Both checks are cheap relative to a write and only run on the ACTIVE branch;
 * deactivating must keep working unconditionally, since refusing to stop a
 * broken concept would leave staff no way out of it.
 */
public class ChangePaymentConceptStatusUseCaseImpl implements ChangePaymentConceptStatusUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	private final PaymentRateRepository paymentRateRepository;

	private final PaymentQuotaCoverageChecker coverageChecker;

	public ChangePaymentConceptStatusUseCaseImpl(PaymentConceptRepository paymentConceptRepository,
			PaymentRateRepository paymentRateRepository, PaymentQuotaCoverageChecker coverageChecker) {
		this.paymentConceptRepository = paymentConceptRepository;
		this.paymentRateRepository = paymentRateRepository;
		this.coverageChecker = coverageChecker;
	}

	@Override
	@Transactional
	public PaymentConceptResult changeStatus(ChangeStatusCommand command) {
		PaymentConcept concept = paymentConceptRepository.findById(command.paymentConceptId())
				.orElseThrow(() -> new PaymentConceptNotFoundException(
						"Payment concept not found: " + command.paymentConceptId()));

		if (command.target() == PaymentConceptStatus.ACTIVE) {
			CreatePaymentConceptUseCaseImpl.validateQuotaLevel(concept.getType(), concept.getLevelNumber(),
					concept.getId(), paymentConceptRepository);
			requireCompleteRateSet(concept);
			concept.activate();
		}
		else {
			concept.deactivate();
		}
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return CreatePaymentConceptUseCaseImpl.toResult(saved);
	}

	/**
	 * Mirrors {@code ReconcilePaymentRatesUseCaseImpl}'s completeness rule, but
	 * against what is <em>stored</em> instead of an incoming payload. The rule
	 * itself is not restated — {@link PaymentQuotaCoverageChecker} owns it, and
	 * this class only chooses which set of rates to hand it.
	 */
	private void requireCompleteRateSet(PaymentConcept concept) {
		Set<UUID> priced = paymentRateRepository.findActiveByConceptId(concept.getId()).stream()
				.map(PaymentRate::getProgramId).filter(Objects::nonNull).collect(Collectors.toSet());

		coverageChecker.requireCompleteCoverage(concept, priced);
	}
}
