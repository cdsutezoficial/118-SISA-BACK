package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link UserRepository} adapter delegating to
 * {@link UserJpaRepository}.
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

	private final UserJpaRepository jpaRepository;

	public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
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
