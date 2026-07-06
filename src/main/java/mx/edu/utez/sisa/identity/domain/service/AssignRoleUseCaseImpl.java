package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Grants a scoped {@code RoleType} to an existing {@code User} (spec:
 * "Assign Role to User"). Enforces the division-required-vs-forbidden rule
 * from {@code 00-shared-kernel.md}.
 */
public class AssignRoleUseCaseImpl implements AssignRoleUseCase {

	private static final Set<RoleType> DIVISION_SCOPED_ROLES = EnumSet.of(RoleType.GESTOR_ACADEMICO,
			RoleType.COORDINACION_ESTADIAS_DIVISION, RoleType.DIRECTOR_DIVISION);

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;

	public AssignRoleUseCaseImpl(UserRepository userRepository, UserRoleRepository userRoleRepository) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Override
	@Transactional
	public AssignRoleResult assignRole(AssignRoleCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(command.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.userId()));

		boolean requiresDivision = DIVISION_SCOPED_ROLES.contains(command.roleType());
		if (requiresDivision && command.divisionId() == null) {
			throw new DivisionRuleViolationException("Role " + command.roleType() + " requires a divisionId");
		}
		if (!requiresDivision && command.divisionId() != null) {
			throw new DivisionRuleViolationException("Role " + command.roleType() + " must not have a divisionId");
		}

		UserRole userRole = new UserRole(target.getId(), command.roleType(), command.divisionId());
		UserRole saved = userRoleRepository.save(userRole);

		return new AssignRoleResult(saved.getId(), saved.getRoleType(), saved.getDivisionId());
	}
}
