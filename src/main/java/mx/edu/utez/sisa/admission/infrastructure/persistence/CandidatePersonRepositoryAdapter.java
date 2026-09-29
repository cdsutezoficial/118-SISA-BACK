package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link CandidatePersonRepository} adapter delegating to
 * {@link CandidatePersonJpaRepository}.
 */
@Component
public class CandidatePersonRepositoryAdapter implements CandidatePersonRepository {

	private final CandidatePersonJpaRepository jpaRepository;

	public CandidatePersonRepositoryAdapter(CandidatePersonJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
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
	public Optional<Person> findById(UUID id) {
		return jpaRepository.findById(id);
	}
}