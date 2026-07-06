package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PersonRepository} adapter delegating to
 * {@link PersonJpaRepository}. Load-only for Identity's own use cases;
 * {@code save} exists only for {@code AdminSeedRunner} and test fixtures
 * (design.md — Decision: Person creation is out of scope).
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
}
