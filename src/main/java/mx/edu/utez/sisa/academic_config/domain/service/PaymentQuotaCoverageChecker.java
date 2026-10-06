package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.AcademicProgramReference;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The rule "a {@code PERIODIC_QUOTA} must price every active career", stated once.
 *
 * <p>
 * Extracted from {@code ReconcilePaymentRatesUseCaseImpl} because a second
 * caller now has to answer the same question — whether an existing set of prices
 * is complete enough for a concept to be a quota — and it did not come from the
 * reconciliation path. {@code UpdatePaymentConceptUseCaseImpl} reaches it when a
 * concept is retyped {@code ENROLLMENT → PERIODIC_QUOTA}: the type change is
 * legal field by field, but the concept becomes one that is illegal while
 * unpriced, and nothing else in the update path looks at rates at all.
 *
 * <p>
 * Both directions are checked, not just the missing one, so the invariant is
 * symmetric: a program that stopped being ACTIVE leaves a row that can neither
 * be priced forward (it is not in {@code findAllActive}) nor removed (the quota
 * refuses a partial set), which would strand the quota with a stale price. That
 * asymmetry is the reason this lives in one place — enforcing only the missing
 * direction is the half of the rule that looks harmless.
 *
 * <p>
 * Not a static helper: it needs {@code findAllActive}, and a static method taking
 * that list as a parameter would push the same call onto both callers and leave
 * one of them free to pass a stale list.
 */
@Component
public class PaymentQuotaCoverageChecker {

	private final AcademicProgramRepository programRepository;

	public PaymentQuotaCoverageChecker(AcademicProgramRepository programRepository) {
		this.programRepository = programRepository;
	}

	/**
	 * No-op unless the concept is a {@code PERIODIC_QUOTA}. The type test is
	 * repeated here rather than trusted from the caller because a concept typed
	 * anything else is allowed a partial set — including an empty one — and
	 * conflating the two is what would make this rule reject valid saves.
	 *
	 * @param pricedProgramIds the careers this concept currently prices with an
	 *                         ACTIVE rate. Only ACTIVE rows count: a superseded
	 *                         price is history, not coverage, and counting it
	 *                         would let a concept claim to price a career it no
	 *                         longer charges.
	 */
	public void requireCompleteCoverage(PaymentConcept concept, Set<UUID> pricedProgramIds) {
		if (concept.getType() != PaymentConceptType.PERIODIC_QUOTA) {
			return;
		}
		requireCompleteCoverage(pricedProgramIds);
	}

	/**
	 * The same check without the type gate, for callers that have already
	 * established the concept is a quota.
	 */
	public void requireCompleteCoverage(Set<UUID> pricedProgramIds) {
		List<AcademicProgramReference> active = programRepository.findAllActive();

		List<String> missing = active.stream()
				.filter(program -> !pricedProgramIds.contains(program.id()))
				.map(AcademicProgramReference::name).toList();

		if (!missing.isEmpty()) {
			throw new IncompletePaymentRateSetException(
					"A PERIODIC_QUOTA concept must price every active program. Missing: " + missing);
		}

		Set<UUID> activeIds = active.stream().map(AcademicProgramReference::id).collect(Collectors.toSet());

		List<String> unexpected = pricedProgramIds.stream().filter(id -> !activeIds.contains(id)).map(UUID::toString)
				.sorted().toList();

		if (!unexpected.isEmpty()) {
			throw new InvalidPaymentRateDataException(
					"A PERIODIC_QUOTA concept can only price active programs; not active: " + unexpected);
		}
	}
}
