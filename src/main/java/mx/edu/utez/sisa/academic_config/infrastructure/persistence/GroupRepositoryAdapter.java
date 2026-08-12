package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link GroupRepository} adapter delegating to
 * {@link GroupJpaRepository}. Results are sorted by {@code code} ascending —
 * the natural human-facing identifier for a group, same convention as
 * {@code GenerationRepositoryAdapter} sorting by {@code code}.
 */
@Component
public class GroupRepositoryAdapter implements GroupRepository {

	private final GroupJpaRepository jpaRepository;

	public GroupRepositoryAdapter(GroupJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Group save(Group group) {
		return jpaRepository.save(group);
	}

	@Override
	public Optional<Group> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public GroupSearchPage search(GroupSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "code"));
		Page<Group> page = jpaRepository.search(criteria.status(), criteria.search(), criteria.programId(),
				criteria.generationId(), pageRequest);
		return new GroupSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
