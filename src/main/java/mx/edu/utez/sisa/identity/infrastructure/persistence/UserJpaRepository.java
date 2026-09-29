package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link UserRepositoryAdapter}.
 */
public interface UserJpaRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsername(String username);

	Optional<User> findByPersonId(UUID personId);

	/**
	 * Backs {@code ListUsersUseCase} (01-identidad.md). {@code roleKey} is
	 * matched with a correlated {@code EXISTS} subquery against
	 * {@code UserRole} — deliberately NOT a {@code JOIN UserRole} — so a user
	 * holding several matching roles is still counted and paginated exactly
	 * once (a JOIN across the one-to-many UserRole relation would duplicate
	 * that user's row per matching role and break both the total count and
	 * the page boundaries). {@code divisionId} is an additive condition
	 * inside that SAME {@code roleKey} EXISTS — not a separate EXISTS of its
	 * own — so "role X scoped to division Y" requires ONE matching
	 * {@code UserRole} row satisfying both, rather than two independent
	 * roles. Because it lives inside the {@code roleKey}-gated EXISTS,
	 * {@code divisionId} is a permissive additive filter that only takes
	 * effect combined with {@code roleKey}: passing it alone, without
	 * {@code roleKey}, has no effect (the outer {@code :roleKey IS NULL OR}
	 * short-circuits before the EXISTS — and thus before {@code divisionId}
	 * — is ever evaluated), matching the endpoint's contract that
	 * {@code divisionId} is not validated as "requires role to also be set".
	 * The free-text {@code search} filter matches {@code username} directly
	 * or, via a second {@code EXISTS} subquery, against the linked
	 * {@code Person}'s individual name fields or their concatenated full
	 * name. All filters are optional: the {@code (:param IS NULL OR ...)}
	 * pattern short-circuits when the caller omits that filter.
	 *
	 * <p>An explicit {@code countQuery} is supplied because Spring Data's
	 * automatic count-query derivation (stripping the SELECT/ORDER BY from
	 * the content query) is not reliable for queries built around correlated
	 * EXISTS subqueries.
	 */
	@Query(value = """
			SELECT u FROM User u
			WHERE (:roleKey IS NULL OR EXISTS (
			        SELECT 1 FROM UserRole ur, Role r WHERE ur.userId = u.id AND ur.roleId = r.id AND r.key = :roleKey
			          AND (:divisionId IS NULL OR ur.divisionId = :divisionId)))
			  AND (:status IS NULL OR u.status = :status)
			  AND (:search IS NULL
			       OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR EXISTS (
			            SELECT 1 FROM Person p WHERE p.id = u.personId AND (
			                LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(p.lastName1) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(p.lastName2) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(CONCAT(p.firstName, ' ', p.lastName1, ' ', COALESCE(p.lastName2, '')))
			                   LIKE LOWER(CONCAT('%', :search, '%')))))
			""",
			countQuery = """
			SELECT COUNT(u) FROM User u
			WHERE (:roleKey IS NULL OR EXISTS (
			        SELECT 1 FROM UserRole ur, Role r WHERE ur.userId = u.id AND ur.roleId = r.id AND r.key = :roleKey
			          AND (:divisionId IS NULL OR ur.divisionId = :divisionId)))
			  AND (:status IS NULL OR u.status = :status)
			  AND (:search IS NULL
			       OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR EXISTS (
			            SELECT 1 FROM Person p WHERE p.id = u.personId AND (
			                LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(p.lastName1) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(p.lastName2) LIKE LOWER(CONCAT('%', :search, '%'))
			                OR LOWER(CONCAT(p.firstName, ' ', p.lastName1, ' ', COALESCE(p.lastName2, '')))
			                   LIKE LOWER(CONCAT('%', :search, '%')))))
			""")
	Page<User> search(@Param("roleKey") String roleKey, @Param("status") UserStatus status,
			@Param("search") String search, @Param("divisionId") UUID divisionId, Pageable pageable);
}
