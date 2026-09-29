package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Full-detail lookup for a single {@code User} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.3).
 * Unlike {@code ListUsersUseCaseImpl}, this maps every {@link UserRole} row
 * with its own {@code userRoleId} exposed — needed by
 * {@code RevokeRoleUseCase} downstream from the detail screen.
 */
public class GetUserUseCaseImpl implements GetUserUseCase {

	private final UserRepository userRepository;
	private final PersonRepository personRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;

	public GetUserUseCaseImpl(UserRepository userRepository, PersonRepository personRepository,
			UserRoleRepository userRoleRepository, RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
	}

	@Override
	public UserDetailResult getUser(GetUserQuery query) {
		User caller = userRepository.findById(query.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + query.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(query.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + query.userId()));
		Person person = personRepository.findById(target.getPersonId()).orElse(null);

		var userRoles = userRoleRepository.findByUserId(target.getId());
		Map<UUID, Role> rolesById = roleRepository.findByIds(userRoles.stream().map(UserRole::getRoleId).distinct().toList())
				.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
		var roles = userRoles.stream().map(role -> rolesById.get(role.getRoleId())).filter(role -> role != null)
				.map(role -> new UserRoleDetail(findUserRoleId(userRoles, role.getId()), role.getId(), role.getKey(),
						role.getName(), findDivisionId(userRoles, role.getId())))
				.toList();

		return new UserDetailResult(target.getId(), target.getPersonId(), fullName(person), target.getUsername(),
				target.getStatus(), target.isMustChangePassword(), target.getLastLoginAt(), target.getCreatedAt(),
				roles);
	}

	/**
	 * Same {@code firstName lastName1 [lastName2]} join as
	 * {@code ListUsersUseCaseImpl#fullName} — duplicated rather than
	 * extracted, matching this module's existing convention of each service
	 * owning its own small mapping helper (no shared mapper class exists
	 * today).
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return "";
		}
		return Stream.of(person.getFirstName(), person.getLastName1(), person.getLastName2())
				.filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
	}

	private static UUID findUserRoleId(java.util.List<UserRole> userRoles, UUID roleId) {
		return userRoles.stream().filter(userRole -> roleId.equals(userRole.getRoleId())).findFirst().map(UserRole::getId)
				.orElse(null);
	}

	private static UUID findDivisionId(java.util.List<UserRole> userRoles, UUID roleId) {
		return userRoles.stream().filter(userRole -> roleId.equals(userRole.getRoleId())).findFirst().map(UserRole::getDivisionId)
				.orElse(null);
	}
}
