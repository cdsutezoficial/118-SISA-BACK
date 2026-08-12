package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchPage;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;

/**
 * Paginated, filterable query for existing {@code Person} records (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2).
 * Mirrors {@code ListUsersUseCaseImpl}'s split of responsibilities: role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, this use case only enforces the caller's
 * mustChangePassword gate and resolves each row's {@code hasUser} flag.
 */
public class ListPersonsUseCaseImpl implements ListPersonsUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;

	public ListPersonsUseCaseImpl(UserRepository userRepository, PersonRepository personRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
	}

	@Override
	public ListPersonsResult listPersons(ListPersonsQuery query) {
		User caller = userRepository.findById(query.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + query.callerId()));
		caller.assertCanOperate();

		PersonSearchCriteria criteria = new PersonSearchCriteria(query.search(), normalizePage(query.page()),
				normalizeSize(query.size()));

		PersonSearchPage page = personRepository.search(criteria);

		return new ListPersonsResult(page.content().stream().map(this::toSummary).toList(), page.totalElements(),
				page.totalPages(), criteria.page(), criteria.size());
	}

	/**
	 * Resolved with one {@code findByPersonId} lookup per row (plan 4.2) —
	 * page sizes here are bounded by {@link #MAX_PAGE_SIZE}, same cost shape
	 * already accepted by {@code ListUsersUseCaseImpl}'s per-page role batch
	 * lookup.
	 */
	private PersonSummary toSummary(Person person) {
		boolean hasUser = userRepository.findByPersonId(person.getId()).isPresent();
		return new PersonSummary(person.getId(), person.getCurp(), person.getFirstName(), person.getLastName1(),
				person.getLastName2(), person.getInstitutionalEmail(), hasUser);
	}

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListPersonsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListPersonsQuery.MAX_PAGE_SIZE);
	}
}
