package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AcademicDivisionRepository} adapter delegating to
 * {@link AcademicDivisionJpaRepository}. Results are sorted by {@code name}
 * ascending (design.md — File Changes: "Adapter, sort by name ASC"),
 * mirroring {@code identity.UserRepositoryAdapter}'s deterministic-pagination
 * rationale for a catalog-style listing screen.
 */
@Component
public class AcademicDivisionRepositoryAdapter implements AcademicDivisionRepository {

	private final AcademicDivisionJpaRepository jpaRepository;

	public AcademicDivisionRepositoryAdapter(AcademicDivisionJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public AcademicDivision save(AcademicDivision division) {
		return jpaRepository.save(division);
	}

	@Override
	public Optional<AcademicDivision> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<AcademicDivision> findByCode(String code) {
		return jpaRepository.findByCodeIgnoreCase(code);
	}

	@Override
	public Optional<AcademicDivision> findByName(String name) {
		return jpaRepository.findByNameIgnoreCase(name);
	}

	@Override
	public DivisionSearchPage search(DivisionSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name"));
		Page<AcademicDivision> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new DivisionSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
