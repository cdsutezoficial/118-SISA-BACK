package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.shared.model.RoleType;

import java.util.List;
import java.util.UUID;

/**
 * Persistence out-port for {@link UserRole}. Implemented by a JPA adapter in
 * Phase 4.
 */
public interface UserRoleRepository {

	UserRole save(UserRole userRole);

	List<UserRole> findByUserId(UUID userId);

	/**
	 * Used by {@code AdminSeedRunner} (design.md — Decision: Bootstrap ADMIN
	 * seed) to detect whether any {@code ADMIN} role already exists, instead
	 * of checking table emptiness.
	 */
	boolean existsByRoleType(RoleType roleType);
}
