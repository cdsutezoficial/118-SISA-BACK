package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentConceptQueryPort} adapter: finds the {@code ACTIVE}
 * {@code ADMISSION} concepts that price the ficha of a program, and resolves
 * how much that program is actually charged from the concept's
 * {@code PaymentRate} history. The type narrowing lives in
 * {@link PaymentConceptLookupJpaRepository#findActiveForProgram}; the
 * rate precedence lives in {@link #pickAmount}.
 */
@Component
public class PaymentConceptQueryAdapter implements PaymentConceptQueryPort {

	/**
	 * Which of the three rungs a rate sits on, most specific first. Lower wins.
	 *
	 * <p>{@code ReconcilePaymentRatesUseCase} guarantees one ACTIVE row per exact
	 * {@code (programId, level, periodId)} combination, so a well-formed catalog
	 * never puts two rows on the same rung. The {@code createdAt} tiebreak below
	 * is what keeps a dirty one from being ambiguous anyway: if two rows somehow
	 * both price the program, the most recently opened one is the one whose
	 * author had the later say.
	 */
	private static final Comparator<PaymentRate> PRECEDENCE = Comparator
			.comparingInt(PaymentConceptQueryAdapter::rung)
			.thenComparing(PaymentRate::getCreatedAt, Comparator.reverseOrder());

	private final PaymentConceptLookupJpaRepository lookupJpaRepository;

	private final PaymentRateLookupJpaRepository rateLookupJpaRepository;

	public PaymentConceptQueryAdapter(PaymentConceptLookupJpaRepository lookupJpaRepository,
			PaymentRateLookupJpaRepository rateLookupJpaRepository) {
		this.lookupJpaRepository = lookupJpaRepository;
		this.rateLookupJpaRepository = rateLookupJpaRepository;
	}

	@Override
	public List<FichaConcept> findActiveEnrollmentForProgram(UUID programId, LocalDate onDate) {
		return lookupJpaRepository
				.findActiveForProgram(PaymentConceptStatus.ACTIVE, PaymentConceptType.ADMISSION,
						programId, onDate, PaymentRateStatus.ACTIVE)
				.stream().map(PaymentConceptQueryAdapter::toFichaConcept).toList();
	}

	@Override
	public List<FichaConcept> findActiveEnrollmentForProgram(UUID programId) {
		return lookupJpaRepository
				.findActiveForProgramIgnoringWindow(PaymentConceptStatus.ACTIVE, PaymentConceptType.ADMISSION,
						programId, PaymentRateStatus.ACTIVE)
				.stream().map(PaymentConceptQueryAdapter::toFichaConcept).toList();
	}

	@Override
	public Optional<BigDecimal> findActiveRateAmountFor(UUID conceptId, UUID programId, LocalDate onDate) {
		return pickAmount(
				rateLookupJpaRepository.findRatesPricableForProgram(conceptId, programId, PaymentRateStatus.ACTIVE));
	}

	/**
	 * The rate that wins among the candidates: the one bound to the program, else
	 * the one bound to its level, else the general one.
	 *
	 * <p>Exposed as a static method taking the list rather than folding the sort
	 * into {@link #findActiveRateAmountFor} so the precedence can be tested as
	 * the rule it is, without a database.
	 */
	static Optional<BigDecimal> pickAmount(List<PaymentRate> candidates) {
		return candidates.stream().min(PRECEDENCE).map(PaymentRate::getAmount);
	}

	/**
	 * 0 = bound to the program, 1 = bound to its level, 2 = bound to neither.
	 * Mirrors the three alternatives OR-ed together in the query.
	 *
	 * <p>Deliberately rung-based and NOT level-aware. It never learns which level
	 * the program has, because {@link PaymentRateLookupJpaRepository} already
	 * correlated it: every level-rate that reaches here belongs to this program by
	 * construction, so comparing levels would be a second, weaker copy of the
	 * same rule. A level rate for some other level is not "less specific" to be
	 * outranked — it is not a candidate, and the query is what keeps it out.
	 */
	private static int rung(PaymentRate rate) {
		if (rate.getProgramId() != null) {
			return 0;
		}
		return rate.getLevel() != null ? 1 : 2;
	}

	/**
	 * The window travels with the concept even on the date-filtered query, where
	 * it is redundant — the caller has already filtered on it. Keeping the mapping
	 * in one place is what stops the two queries from drifting into returning
	 * different shapes for the same record.
	 */
	private static FichaConcept toFichaConcept(PaymentConcept c) {
		return new FichaConcept(c.getId(), c.getName(), c.getAvailableFrom(), c.getAvailableUntil());
	}
}