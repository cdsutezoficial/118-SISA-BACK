package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link HighSchoolTypeRepository} adapter delegating to
 * {@link HighSchoolTypeJpaRepository}. Results are sorted by {@code name}
 * ascending then {@code id} ascending — same "no uniqueness, id as
 * deterministic tie-breaker" rationale as
 * {@code OutreachChannelRepositoryAdapter}.
 */
@Component
public class HighSchoolTypeRepositoryAdapter implements HighSchoolTypeRepository {

	private final HighSchoolTypeJpaRepository jpaRepository;

	public HighSchoolTypeRepositoryAdapter(HighSchoolTypeJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public HighSchoolType save(HighSchoolType highSchoolType) {
		return jpaRepository.save(highSchoolType);
	}

	@Override
	public Optional<HighSchoolType> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public HighSchoolTypeSearchPage search(HighSchoolTypeSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<HighSchoolType> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new HighSchoolTypeSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
