package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.stereotype.Component;

import java.util.List;
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
	public boolean existsByRoleType(RoleType roleType) {
		return jpaRepository.existsByRoleType(roleType);
	}
}
