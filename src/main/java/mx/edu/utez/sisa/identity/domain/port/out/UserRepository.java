package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.User;

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
}
