package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Revokes a single scoped role grant from a {@code User} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.4, the
 * inverse of {@code AssignRoleUseCase}). Restricted to ADMIN callers, same
 * level as {@code AssignRoleUseCase}. Implementations must validate that the
 * {@code UserRole} identified by {@code userRoleId} actually belongs to
 * {@code userId} — evaluated together, not independently, so a caller cannot
 * revoke another user's role grant by supplying a mismatched pair (plan 4.4:
 * "evita revocar el rol de otro usuario adivinando un id"). Deliberately
 * minimalist per the plan's explicit scope note: no "must keep at least one
 * role" or "must keep at least one ADMIN in the system" rule — undocumented
 * anywhere, added later only if a real need appears.
 */
public interface RevokeRoleUseCase {

	void revokeRole(RevokeRoleCommand command);

	/**
	 * @param callerId   the acting ADMIN user, used for the mustChangePassword guard and authorization
	 * @param userId     the {@code User} the role grant must belong to
	 * @param userRoleId the specific role grant to delete
	 */
	record RevokeRoleCommand(UUID callerId, UUID userId, UUID userRoleId) {
	}
}
