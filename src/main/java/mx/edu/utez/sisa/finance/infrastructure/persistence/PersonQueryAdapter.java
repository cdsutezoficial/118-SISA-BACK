package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PersonQueryPort} adapter reading shared-kernel
 * {@code Person}.
 */
@Component
public class PersonQueryAdapter implements PersonQueryPort {

	private final FinancePersonJpaRepository jpaRepository;

	public PersonQueryAdapter(FinancePersonJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<PersonView> findById(UUID id) {
		return jpaRepository.findById(id).map(PersonQueryAdapter::toView);
	}

	private static PersonView toView(Person person) {
		return new PersonView(person.getId(), person.getCurp(), person.getFirstName(), person.getLastName1(),
				person.getLastName2());
	}
}
