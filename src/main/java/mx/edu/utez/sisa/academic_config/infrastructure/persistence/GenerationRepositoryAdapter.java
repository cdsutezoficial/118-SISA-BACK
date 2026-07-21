package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link GenerationRepository} adapter delegating to
 * {@link GenerationJpaRepository}. Results are sorted by {@code code}
 * ascending — the natural human-facing identifier for a generation, same
 * convention as {@code AcademicPlanRepositoryAdapter} sorting by
 * {@code version}.
 */
@Component
public class GenerationRepositoryAdapter implements GenerationRepository {

	private final GenerationJpaRepository jpaRepository;

	public GenerationRepositoryAdapter(GenerationJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Generation save(Generation generation) {
		return jpaRepository.save(generation);
	}

	@Override
	public Optional<Generation> findByProgramIdAndNumber(UUID programId, int number) {
		return jpaRepository.findByProgramIdAndNumber(programId, number);
	}

	@Override
	public Optional<Generation> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public GenerationSearchPage search(GenerationSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "code"));
		Page<Generation> page = jpaRepository.search(criteria.status(), criteria.search(), criteria.programId(),
				pageRequest);
		return new GenerationSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
