package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
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
	public Optional<PaymentRate> findActive(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId) {
		return jpaRepository.findActive(conceptId, programId, level, periodId, PaymentRateStatus.ACTIVE);
	}

	@Override
	public List<PaymentRate> findHistoryByConceptId(UUID conceptId) {
		return jpaRepository.findHistoryByConceptId(conceptId);
	}

	@Override
	public List<PaymentRate> findActiveByConceptId(UUID conceptId) {
		return jpaRepository.findActiveByConceptId(conceptId, PaymentRateStatus.ACTIVE);
	}
}