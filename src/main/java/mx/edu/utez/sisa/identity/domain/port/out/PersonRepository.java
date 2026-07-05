package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.shared.model.Person;

import java.util.Optional;
import java.util.UUID;

/**
 * Out-port over the shared-kernel {@link Person}. Load-only for Identity's
 * own use cases; {@code save} exists only for {@code AdminSeedRunner} and
 * test fixtures (design.md — Decision: Person creation is out of scope).
 */
public interface PersonRepository {

	Optional<Person> findById(UUID id);

	Person save(Person person);
}
