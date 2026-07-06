package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface backing {@link UserRoleRepositoryAdapter}.
 */
public interface UserRoleJpaRepository extends JpaRepository<UserRole, UUID> {

	List<UserRole> findByUserId(UUID userId);

	boolean existsByRoleType(RoleType roleType);
}
