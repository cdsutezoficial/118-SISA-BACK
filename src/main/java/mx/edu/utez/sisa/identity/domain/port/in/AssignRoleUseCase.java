package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.shared.model.RoleType;

import java.util.UUID;

/**
 * Grants a scoped {@code RoleType} to an existing {@code User} (spec:
 * "Assign Role to User"). Restricted to ADMIN callers; implementations must
 * enforce the division-required-vs-forbidden rule and the caller's
 * {@code assertCanOperate()} gate.
 */
public interface AssignRoleUseCase {

	AssignRoleResult assignRole(AssignRoleCommand command);

	/**
	 * @param callerId   the acting ADMIN user, used for the mustChangePassword guard and authorization
	 * @param userId     the target {@code User} receiving the role
	 * @param roleType   the role being granted
	 * @param divisionId required for division-scoped roles, forbidden otherwise (spec rule)
	 */
	record AssignRoleCommand(UUID callerId, UUID userId, RoleType roleType, UUID divisionId) {
	}

	record AssignRoleResult(UUID userRoleId, RoleType roleType, UUID divisionId) {
	}
}
