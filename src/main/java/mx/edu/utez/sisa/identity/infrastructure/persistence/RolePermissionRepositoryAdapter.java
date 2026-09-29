package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RolePermissionRepositoryAdapter implements RolePermissionRepository {

	private final RolePermissionJpaRepository jpaRepository;

	public RolePermissionRepositoryAdapter(RolePermissionJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public RolePermission save(RolePermission rolePermission) {
		return jpaRepository.save(rolePermission);
	}

	@Override
	public List<RolePermission> saveAll(List<RolePermission> rolePermissions) {
		return jpaRepository.saveAll(rolePermissions);
	}

	@Override
	public List<RolePermission> findByRoleId(UUID roleId) {
		return jpaRepository.findByRoleId(roleId);
	}

	@Override
	public void deleteByRoleId(UUID roleId) {
		jpaRepository.deleteByRoleId(roleId);
	}
}