package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentAreaRepository} adapter delegating to
 * {@link PaymentAreaJpaRepository}. Results are sorted by {@code name}
 * ascending then {@code id} ascending for deterministic pagination, mirroring
 * {@code AcademicDivisionRepositoryAdapter}.
 */
@Component
public class PaymentAreaRepositoryAdapter implements PaymentAreaRepository {

	private final PaymentAreaJpaRepository jpaRepository;

	public PaymentAreaRepositoryAdapter(PaymentAreaJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentArea save(PaymentArea area) {
		return jpaRepository.save(area);
	}

	@Override
	public Optional<PaymentArea> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<PaymentArea> findByCode(String code) {
		return jpaRepository.findByCodeIgnoreCase(code);
	}

	@Override
	public Optional<PaymentArea> findByName(String name) {
		return jpaRepository.findByNameIgnoreCase(name);
	}

	@Override
	public PaymentAreaSearchPage search(PaymentAreaSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<PaymentArea> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new PaymentAreaSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
