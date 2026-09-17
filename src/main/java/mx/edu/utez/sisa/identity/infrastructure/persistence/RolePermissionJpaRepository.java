package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermission, UUID> {

	List<RolePermission> findByRoleId(UUID roleId);

	void deleteByRoleId(UUID roleId);
}