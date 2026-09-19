package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RoleRepositoryAdapter implements RoleRepository {

	private final RoleJpaRepository jpaRepository;

	public RoleRepositoryAdapter(RoleJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Role save(Role role) {
		return jpaRepository.save(role);
	}

	@Override
	public Optional<Role> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<Role> findByKey(String key) {
		return jpaRepository.findByKey(key);
	}

	@Override
	public List<Role> findByIds(List<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findByIdIn(ids);
	}

	@Override
	public boolean existsByKey(String key) {
		return jpaRepository.existsByKey(key);
	}

	@Override
	public boolean existsByKeyAndIdNot(String key, UUID id) {
		return jpaRepository.existsByKeyAndIdNot(key, id);
	}

	@Override
	public RoleSearchPage search(RoleSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "name"));
		Page<Role> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new RoleSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}