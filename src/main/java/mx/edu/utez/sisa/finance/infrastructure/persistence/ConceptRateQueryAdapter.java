package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link ConceptRateQueryPort} adapter reading
 * {@code academic_config.PaymentRate}. Walks the same three-rung ladder the
 * admission flow does — exact program, then the program's level, then
 * neither — and breaks ties on the most recently created row.
 *
 * <p>The ladder lives here in Java rather than in the query so it stays
 * visible to the tests that guard it; the query only narrows to the rows that
 * <em>could</em> apply. This is an intentional duplicate of
 * {@code admission.PaymentConceptQueryAdapter}'s precedence: promoting it to
 * {@code shared} is a follow-up, not a Fase 1 change to the admission context.
 */
@Component
public class ConceptRateQueryAdapter implements ConceptRateQueryPort {

	private static final Comparator<PaymentRate> PRECEDENCE = Comparator
			.comparingInt(ConceptRateQueryAdapter::rung)
			.thenComparing(PaymentRate::getCreatedAt, Comparator.reverseOrder());

	private final FinanceRateLookupJpaRepository rateLookupJpaRepository;

	public ConceptRateQueryAdapter(FinanceRateLookupJpaRepository rateLookupJpaRepository) {
		this.rateLookupJpaRepository = rateLookupJpaRepository;
	}

	@Override
	public Optional<BigDecimal> findActiveAmount(UUID conceptId, UUID programId, AcademicLevel level) {
		return pickAmount(rateLookupJpaRepository.findRatesPricableForProgram(conceptId, programId,
				PaymentRateStatus.ACTIVE));
	}

	@Override
	public Optional<ActiveRate> findActiveRate(UUID conceptId, UUID programId) {
		return rateLookupJpaRepository
				.findRatesPricableForProgram(conceptId, programId, PaymentRateStatus.ACTIVE).stream()
				.min(PRECEDENCE).map(rate -> new ActiveRate(rate.getId(), rate.getAmount()));
	}

	static Optional<BigDecimal> pickAmount(List<PaymentRate> candidates) {
		return candidates.stream().min(PRECEDENCE).map(PaymentRate::getAmount);
	}

	private static int rung(PaymentRate rate) {
		if (rate.getProgramId() != null) {
			return 0;
		}
		if (rate.getLevel() != null) {
			return 1;
		}
		return 2;
	}
}
