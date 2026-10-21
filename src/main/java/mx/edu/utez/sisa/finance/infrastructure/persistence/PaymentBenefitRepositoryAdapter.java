package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentBenefitRepository} adapter. The concept-code match
 * is case-insensitive at the Spring Data selector, mirroring the catalog's own
 * case-insensitive code lookups.
 */
@Component
public class PaymentBenefitRepositoryAdapter implements PaymentBenefitRepository {

	private final PaymentBenefitJpaRepository jpaRepository;

	public PaymentBenefitRepositoryAdapter(PaymentBenefitJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentBenefit save(PaymentBenefit benefit) {
		return jpaRepository.save(benefit);
	}

	@Override
	public Optional<PaymentBenefit> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public List<PaymentBenefit> findActiveByCandidateAndConcept(UUID candidateId, String conceptCode) {
		return jpaRepository.findByCandidateIdAndConceptCodeIgnoreCaseAndStatus(candidateId, conceptCode,
				BenefitStatus.ACTIVE);
	}

	@Override
	public List<PaymentBenefit> findActiveByStudentAndConcept(UUID studentId, String conceptCode) {
		return jpaRepository.findByStudentIdAndConceptCodeIgnoreCaseAndStatus(studentId, conceptCode,
				BenefitStatus.ACTIVE);
	}

	@Override
	public List<PaymentBenefit> search(BenefitSearchCriteria criteria) {
		return jpaRepository.search(criteria.candidateId(), criteria.studentId(), criteria.conceptCode(),
				criteria.status(), criteria.createdFrom(), criteria.createdTo());
	}
}
