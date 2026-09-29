package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PersonRepository} adapter delegating to
 * {@link PersonJpaRepository}. {@code save} is used both by
 * {@code TestAccountsSeedRunner}/test fixtures (original usage) and now by
 * {@code CreatePersonUseCaseImpl} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1).
 */
@Component
public class PersonRepositoryAdapter implements PersonRepository {

	private final PersonJpaRepository jpaRepository;

	public PersonRepositoryAdapter(PersonJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<Person> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Person save(Person person) {
		return jpaRepository.save(person);
	}

	@Override
	public Optional<Person> findByCurp(String curp) {
		return jpaRepository.findByCurp(curp);
	}

	@Override
	public Optional<Person> findByInstitutionalEmail(String institutionalEmail) {
		return jpaRepository.findByInstitutionalEmail(institutionalEmail);
	}

	/**
	 * Sorted by {@code curp} ascending, same rationale as
	 * {@code UserRepositoryAdapter#search}: no ordering is specified in the
	 * plan, and a stable order keeps pagination deterministic across pages.
	 */
	@Override
	public PersonSearchPage search(PersonSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.ASC, "curp"));
		Page<Person> page = jpaRepository.search(criteria.search(), pageRequest);
		return new PersonSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
