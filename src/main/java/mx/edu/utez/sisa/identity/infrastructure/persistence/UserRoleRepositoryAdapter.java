package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link UserRoleRepository} adapter delegating to
 * {@link UserRoleJpaRepository}.
 */
@Component
public class UserRoleRepositoryAdapter implements UserRoleRepository {

	private final UserRoleJpaRepository jpaRepository;

	public UserRoleRepositoryAdapter(UserRoleJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public UserRole save(UserRole userRole) {
		return jpaRepository.save(userRole);
	}

	@Override
	public List<UserRole> findByUserId(UUID userId) {
		return jpaRepository.findByUserId(userId);
	}

	@Override
	public boolean existsByRoleId(UUID roleId) {
		return jpaRepository.existsByRoleId(roleId);
	}

	@Override
	public List<UserRole> findByUserIdIn(List<UUID> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findByUserIdIn(userIds);
	}

	@Override
	public Optional<UserRole> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public void delete(UserRole userRole) {
		jpaRepository.delete(userRole);
	}
}
