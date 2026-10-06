package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Minimal read-only view over identity's {@code user_role} / {@code role}
 * tables, backing {@link CallerDivisionScopeAdapter}. Deliberately NOT identity's
 * own {@code UserRoleRepository} or {@code UserJpaRepository}: {@code admission}
 * defines its own tiny repository for the exact query it needs, the same
 * convention {@code ProgramAdmissionConfigLookupJpaRepository} follows for
 * {@code academic_config}'s entities.
 *
 * <p>Resolves the role by joining {@code role_id → role.key} rather than
 * filtering on {@code UserRole}'s {@code role_type} column: that column is a
 * legacy, never-written mapping, so it cannot be relied on for newly-assigned
 * grants.
 */
public interface CallerUserRoleJpaRepository extends JpaRepository<UserRole, UUID> {

	/**
	 * Every grant the user holds for the given role key. A list rather than an
	 * {@code Optional} because the query must be able to distinguish "no such
	 * grant" (empty list → unrestricted caller) from "grant present, division
	 * column null" (one element with a null division → restricted but
	 * division-less), and an {@code Optional<UserRole>} would collapse both to
	 * empty.
	 */
	@Query("""
			SELECT ur FROM UserRole ur, Role r
			WHERE ur.userId = :userId AND ur.roleId = r.id AND r.key = :roleKey
			""")
	List<UserRole> findGrantsByUserAndRoleKey(@Param("userId") UUID userId, @Param("roleKey") String roleKey);
}
