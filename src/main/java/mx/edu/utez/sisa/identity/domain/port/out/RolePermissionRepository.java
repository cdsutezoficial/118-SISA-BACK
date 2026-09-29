package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.RolePermission;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository {

	RolePermission save(RolePermission rolePermission);

	List<RolePermission> saveAll(List<RolePermission> rolePermissions);

	List<RolePermission> findByRoleId(UUID roleId);

	void deleteByRoleId(UUID roleId);
}