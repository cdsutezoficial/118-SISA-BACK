package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleScopePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Implements {@link UserRoleScopePort} on top of {@code identity}'s own
 * repositories — no foreign tables involved, only {@code User}, {@code Role}
 * and {@code UserRole} reads plus a single {@code user_role} write.
 *
 * <p>Composed from the existing ports rather than its own Spring Data
 * repository so the resolution rules (a username with several grants, the
 * role-key indirection) stay in Java and the whole operation stays in one
 * transaction.
 */
@Component
public class UserRoleScopeAdapter implements UserRoleScopePort {

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final UserRoleRepository userRoleRepository;

	public UserRoleScopeAdapter(UserRepository userRepository, RoleRepository roleRepository,
			UserRoleRepository userRoleRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Override
	@Transactional
	public boolean scopeDivisionByUsername(String username, String roleKey, UUID divisionId) {
		Objects.requireNonNull(divisionId, "divisionId is required to scope a role grant");

		User user = userRepository.findByUsername(username).orElse(null);
		if (user == null) {
			return false;
		}
		Role role = roleRepository.findByKey(roleKey).orElse(null);
		if (role == null) {
			return false;
		}
		UserRole grant = userRoleRepository.findByUserId(user.getId()).stream()
				.filter(candidate -> candidate.getRoleId().equals(role.getId())).findFirst().orElse(null);
		if (grant == null) {
			return false;
		}
		// Avoid a pointless write on every re-run of the seed.
		if (divisionId.equals(grant.getDivisionId())) {
			return true;
		}
		grant.scopeDivision(divisionId);
		userRoleRepository.save(grant);
		return true;
	}
}