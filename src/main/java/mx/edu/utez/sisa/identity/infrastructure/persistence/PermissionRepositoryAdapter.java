package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PermissionRepositoryAdapter implements PermissionRepository {

	private final PermissionJpaRepository jpaRepository;

	public PermissionRepositoryAdapter(PermissionJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Permission save(Permission permission) {
		return jpaRepository.save(permission);
	}

	@Override
	public Optional<Permission> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<Permission> findByKey(String key) {
		return jpaRepository.findByKey(key);
	}

	@Override
	public List<Permission> findByIds(List<UUID> ids) {
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
	public PermissionSearchPage search(PermissionSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "name"));
		Page<Permission> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new PermissionSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}