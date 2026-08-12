package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentConceptRepository} adapter delegating to
 * {@link PaymentConceptJpaRepository}. Results are sorted by {@code name}
 * ascending then {@code id} ascending — {@code name} is NOT unique for this
 * aggregate and, unlike {@code SubjectClassification}, there is no
 * {@code code} field to use as a tie-breaker, so {@code id} (always unique)
 * is used instead for deterministic pagination across pages.
 */
@Component
public class PaymentConceptRepositoryAdapter implements PaymentConceptRepository {

	private final PaymentConceptJpaRepository jpaRepository;

	public PaymentConceptRepositoryAdapter(PaymentConceptJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentConcept save(PaymentConcept concept) {
		return jpaRepository.save(concept);
	}

	@Override
	public Optional<PaymentConcept> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public PaymentConceptSearchPage search(PaymentConceptSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<PaymentConcept> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new PaymentConceptSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
