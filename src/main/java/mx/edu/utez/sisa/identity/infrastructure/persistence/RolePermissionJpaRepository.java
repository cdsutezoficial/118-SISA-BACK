package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermission, UUID> {

	List<RolePermission> findByRoleId(UUID roleId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RolePermission rp where rp.roleId = :roleId")
	void deleteByRoleId(@Param("roleId") UUID roleId);
}