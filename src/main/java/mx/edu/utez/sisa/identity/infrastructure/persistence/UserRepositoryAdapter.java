package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JPA-backed {@link UserRepository} adapter delegating to
 * {@link UserJpaRepository}.
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

	private final UserJpaRepository jpaRepository;

	private final PersonJpaRepository personJpaRepository;

	public UserRepositoryAdapter(UserJpaRepository jpaRepository, PersonJpaRepository personJpaRepository) {
		this.jpaRepository = jpaRepository;
		this.personJpaRepository = personJpaRepository;
	}

	/**
	 * Delegates the roleType/status/search filtering and pagination to
	 * {@link UserJpaRepository#search} (the query itself carries the
	 * EXISTS-not-JOIN duplication-avoidance reasoning). This adapter's own
	 * job is pairing each returned {@code User} with its {@code Person} via a
	 * single batch {@code findAllById} — deliberately not N+1 per-row lookups
	 * — and translating Spring Data's {@code Page} into the plain
	 * {@code UserSearchPage} the domain out-port returns. Results are sorted
	 * by {@code username} ascending: no ordering is specified in the domain
	 * doc, and a stable alphabetical order keeps pagination deterministic
	 * across pages for a staff-directory-style listing screen.
	 */
	@Override
	public UserSearchPage search(UserSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "username"));
		Page<User> page = jpaRepository.search(criteria.roleType(), criteria.status(), criteria.search(),
				criteria.divisionId(), pageRequest);

		List<User> users = page.getContent();
		List<UUID> personIds = users.stream().map(User::getPersonId).distinct().toList();
		Map<UUID, Person> personsById = personJpaRepository.findAllById(personIds).stream()
				.collect(Collectors.toMap(Person::getId, Function.identity()));

		List<UserWithPerson> content = users.stream()
				.map(user -> new UserWithPerson(user, personsById.get(user.getPersonId()))).toList();

		return new UserSearchPage(content, page.getTotalElements(), page.getTotalPages());
	}

	@Override
	public User save(User user) {
		return jpaRepository.save(user);
	}

	@Override
	public Optional<User> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<User> findByUsername(String username) {
		return jpaRepository.findByUsername(username);
	}

	@Override
	public Optional<User> findByPersonId(UUID personId) {
		return jpaRepository.findByPersonId(personId);
	}
}
