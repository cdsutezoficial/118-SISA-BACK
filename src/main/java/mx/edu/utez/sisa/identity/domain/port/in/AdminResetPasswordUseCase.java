package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Restores access for a user who cannot use the self-service recovery flow —
 * no access to their institutional mailbox, or their account is locked out
 * (plan {@code 2026-10-02-admin-reset-password.md}).
 *
 * <p>Complements {@link RequestPasswordResetUseCase}, which requires the user
 * to already hold the mailbox: an ADMIN asking the user "what's your new
 * password?" in person cannot use a link the user would have to click, so this
 * assigns the credential directly and hands it back to the caller.
 *
 * <p>Restricted to ADMIN callers, mirroring {@link CreateUserUseCase}.
 */
public interface AdminResetPasswordUseCase {

	AdminResetPasswordResult reset(AdminResetPasswordCommand command);

	/**
	 * @param callerId     the acting ADMIN user, subject to the same
	 *                     {@code assertCanOperate()} gate as user creation
	 * @param targetUserId the user whose password is being replaced
	 */
	record AdminResetPasswordCommand(UUID callerId, UUID targetUserId) {
	}

	/**
	 * @param temporaryPassword the generated plaintext, returned so the ADMIN
	 *                          can hand it over. It exists in memory only: just
	 *                          the BCrypt hash is persisted, and the caller
	 *                          cannot read it back afterwards.
	 */
	record AdminResetPasswordResult(UUID userId, String username, String temporaryPassword) {
	}
}