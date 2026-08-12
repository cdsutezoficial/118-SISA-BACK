package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.UserStatus;

import java.util.UUID;

/**
 * Manually reverses a {@code User}'s account lock (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.5). Today
 * a {@code LOCKED} account (after 3 failed logins, {@code User#registerFailedLogin})
 * has no way back — this use case is that path. Restricted to ADMIN callers,
 * same level as {@code AssignRoleUseCase}/{@code RevokeRoleUseCase}.
 */
public interface UnlockUserUseCase {

	UnlockUserResult unlockUser(UnlockUserCommand command);

	/**
	 * @param callerId the acting ADMIN user, used for the mustChangePassword guard and authorization
	 * @param userId   the target {@code User} to unlock
	 */
	record UnlockUserCommand(UUID callerId, UUID userId) {
	}

	record UnlockUserResult(UUID userId, UserStatus status, int failedLoginAttempts) {
	}
}
