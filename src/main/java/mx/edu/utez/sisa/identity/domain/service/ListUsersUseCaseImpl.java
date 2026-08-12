package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchPage;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserWithPerson;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Paginated, filterable query for registered users (01-identidad.md — Puertos
 * (in): {@code ListUsersUseCase}). Role-based authorization (ADMIN or
 * SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not here
 * — this use case only enforces the caller's mustChangePassword gate, the
 * same split of responsibilities used by CreateUserUseCaseImpl and
 * AssignRoleUseCaseImpl.
 */
public class ListUsersUseCaseImpl implements ListUsersUseCase {

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;

	public ListUsersUseCaseImpl(UserRepository userRepository, UserRoleRepository userRoleRepository) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Override
	public ListUsersResult listUsers(ListUsersQuery query) {
		User caller = userRepository.findById(query.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + query.callerId()));
		caller.assertCanOperate();

		UserSearchCriteria criteria = new UserSearchCriteria(query.roleType(), query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()), query.divisionId());

		UserSearchPage page = userRepository.search(criteria);

		List<UUID> userIds = page.content().stream().map(row -> row.user().getId()).toList();
		Map<UUID, List<UserRole>> rolesByUserId = userRoleRepository.findByUserIdIn(userIds).stream()
				.collect(Collectors.groupingBy(UserRole::getUserId));

		List<UserSummary> summaries = page.content().stream()
				.map(row -> toSummary(row, rolesByUserId.getOrDefault(row.user().getId(), List.of()))).toList();

		return new ListUsersResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private UserSummary toSummary(UserWithPerson row, List<UserRole> roles) {
		User user = row.user();
		List<UserRoleSummary> roleSummaries = roles.stream()
				.map(role -> new UserRoleSummary(role.getRoleType(), role.getDivisionId())).toList();
		return new UserSummary(user.getId(), user.getPersonId(), fullName(row.person()), user.getUsername(),
				roleSummaries, user.getStatus(), user.getLastLoginAt());
	}

	/**
	 * Joins {@code firstName lastName1 [lastName2]}, omitting the second last
	 * name when absent (spec: person full name display). Falls back to an
	 * empty string if {@code person} is {@code null} — defensive only; the
	 * {@code personId} FK invariant means this should never happen in
	 * practice.
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return "";
		}
		return Stream.of(person.getFirstName(), person.getLastName1(), person.getLastName2())
				.filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — a query-endpoint is not the place to fail hard on a caller
	 * mistake that has an obvious, safe default.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to {@link ListUsersQuery#DEFAULT_PAGE_SIZE};
	 * oversized requests are capped at {@link ListUsersQuery#MAX_PAGE_SIZE} to
	 * bound the EXISTS-subquery cost per page.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListUsersQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListUsersQuery.MAX_PAGE_SIZE);
	}
}
