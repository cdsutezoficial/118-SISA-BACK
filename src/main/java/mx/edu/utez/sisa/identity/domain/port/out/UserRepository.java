package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;

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
	 * (01-identidad.md). {@code roleType} is matched via an EXISTS-style
	 * predicate against the one-to-many {@code UserRole} relation — never a
	 * JOIN on {@code UserRole} — so a user with multiple matching roles is
	 * counted and paginated exactly once.
	 */
	UserSearchPage search(UserSearchCriteria criteria);

	/**
	 * @param roleType optional — filters to users having at least one UserRole of this type
	 * @param status   optional — filters to users with this exact status
	 * @param search   optional free-text match against username or the linked Person's full name
	 * @param page     zero-based page index
	 * @param size     page size
	 */
	record UserSearchCriteria(RoleType roleType, UserStatus status, String search, int page, int size) {
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
