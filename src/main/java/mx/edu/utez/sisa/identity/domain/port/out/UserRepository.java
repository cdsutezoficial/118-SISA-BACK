package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link User}. Implemented by a JPA adapter in
 * Phase 4.
 */
public interface UserRepository {

	User save(User user);

	Optional<User> findById(UUID id);

	Optional<User> findByUsername(String username);

	Optional<User> findByPersonId(UUID personId);

	/**
	 * Filterable, paginated query backing {@code ListUsersUseCase}
	 * (01-identidad.md). {@code roleKey} is matched via an EXISTS-style
	 * predicate against the one-to-many {@code UserRole} relation — never a
	 * JOIN on {@code UserRole} — so a user with multiple matching roles is
	 * counted and paginated exactly once.
	 */
	UserSearchPage search(UserSearchCriteria criteria);

	/**
	 * @param roleKey    optional — filters to users having at least one UserRole with this role key
	 * @param status     optional — filters to users with this exact status
	 * @param search     optional free-text match against username or the linked Person's full name
	 * @param page       zero-based page index
	 * @param size       page size
	 * @param divisionId optional — narrows the {@code roleKey} EXISTS predicate against
	 *                   {@code UserRole} to roles scoped to this division, so "role X scoped to
	 *                   division Y" requires a single matching UserRole row rather than two
	 *                   independent roles. Only takes effect when combined with {@code roleKey}:
	 *                   passed alone it has no effect (permissive, not validated as requiring
	 *                   {@code roleKey} to also be set).
	 */
	record UserSearchCriteria(String roleKey, UserStatus status, String search, int page, int size,
			UUID divisionId) {
	}

	/**
	 * @param content        the {@link User} rows for the requested page, paired with their {@link Person}
	 * @param totalElements  total matching rows across all pages
	 * @param totalPages     total page count for {@code totalElements} at the requested page size
	 */
	record UserSearchPage(List<UserWithPerson> content, long totalElements, int totalPages) {
	}

	record UserWithPerson(User user, Person person) {
	}
}
