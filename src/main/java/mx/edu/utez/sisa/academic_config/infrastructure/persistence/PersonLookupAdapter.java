package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JPA-backed {@link PersonLookupPort} adapter delegating to
 * {@link PersonLookupJpaRepository}. Read-only: only presence, never the full
 * {@code Person} shape.
 */
@Component
public class PersonLookupAdapter implements PersonLookupPort {

	private final PersonLookupJpaRepository jpaRepository;

	public PersonLookupAdapter(PersonLookupJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public boolean existsById(UUID personId) {
		return jpaRepository.existsById(personId);
	}
}
