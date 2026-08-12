package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentRateRepository} adapter delegating to
 * {@link PaymentRateJpaRepository}.
 */
@Component
public class PaymentRateRepositoryAdapter implements PaymentRateRepository {

	private final PaymentRateJpaRepository jpaRepository;

	public PaymentRateRepositoryAdapter(PaymentRateJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentRate save(PaymentRate rate) {
		return jpaRepository.save(rate);
	}

	@Override
	public Optional<PaymentRate> findActiveContinuousRate(UUID conceptId, UUID programId, AcademicLevel level) {
		return jpaRepository.findActiveContinuousRate(conceptId, programId, level);
	}

	@Override
	public boolean existsByExactCombination(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId) {
		return jpaRepository.existsByExactCombination(conceptId, programId, level, periodId);
	}

	@Override
	public List<PaymentRate> findHistoryByConceptId(UUID conceptId) {
		return jpaRepository.findHistoryByConceptId(conceptId);
	}
}
