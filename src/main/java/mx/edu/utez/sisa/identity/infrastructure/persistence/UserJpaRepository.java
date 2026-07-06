package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link UserRepositoryAdapter}.
 */
public interface UserJpaRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsername(String username);

	Optional<User> findByPersonId(UUID personId);
}
