package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Grants a scoped {@code RoleType} to an existing {@code User} (spec:
 * "Assign Role to User"). Enforces the division-required-vs-forbidden rule
 * from {@code 00-shared-kernel.md}.
 */
public class AssignRoleUseCaseImpl implements AssignRoleUseCase {

	private static final Set<String> DIVISION_SCOPED_ROLES = Set.of(RoleType.GESTOR_ACADEMICO.name(),
			RoleType.COORDINACION_ESTADIAS_DIVISION.name(), RoleType.DIRECTOR_DIVISION.name());

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;

	public AssignRoleUseCaseImpl(UserRepository userRepository, UserRoleRepository userRoleRepository,
			RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
	}

	@Override
	@Transactional
	public AssignRoleResult assignRole(AssignRoleCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(command.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.userId()));

		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId()));

		boolean requiresDivision = DIVISION_SCOPED_ROLES.contains(role.getKey());
		if (requiresDivision && command.divisionId() == null) {
			throw new DivisionRuleViolationException("Debes seleccionar una división para asignar este rol.");
		}
		if (!requiresDivision && command.divisionId() != null) {
			throw new DivisionRuleViolationException("La división seleccionada no aplica para este rol.");
		}

		UserRole userRole = new UserRole(target.getId(), role.getId(), command.divisionId());
		UserRole saved = userRoleRepository.save(userRole);

		return new AssignRoleResult(saved.getId(), role.getId(), role.getKey(), role.getName(), saved.getDivisionId());
	}
}
