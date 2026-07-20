package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed {@link SubjectClassificationRepository} adapter delegating to
 * {@link SubjectClassificationJpaRepository}. Results are sorted by
 * {@code name} ascending then {@code code} ascending — {@code name} alone is
 * NOT unique for this aggregate (unlike {@code AcademicDivision}), so
 * {@code code} (which IS unique) is added as a tie-breaker for deterministic
 * pagination across pages.
 */
@Component
public class SubjectClassificationRepositoryAdapter implements SubjectClassificationRepository {

	private final SubjectClassificationJpaRepository jpaRepository;

	public SubjectClassificationRepositoryAdapter(SubjectClassificationJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public SubjectClassification save(SubjectClassification classification) {
		return jpaRepository.save(classification);
	}

	@Override
	public Optional<SubjectClassification> findByCode(String code) {
		return jpaRepository.findByCodeIgnoreCase(code);
	}

	@Override
	public ClassificationSearchPage search(ClassificationSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "code")));
		Page<SubjectClassification> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new ClassificationSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
