package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Changes the caller's own password (spec: "Mandatory First-Access Password
 * Change"). This is the one operation exempt from
 * {@code User.assertCanOperate()} — it is what lifts the block.
 */
public interface ChangePasswordUseCase {

	void changePassword(ChangePasswordCommand command);

	/**
	 * @param userId          the {@code User} changing their own password
	 * @param currentPassword must match the stored hash or the change is rejected
	 * @param newPassword     the new plaintext password, hashed by the implementation
	 */
	record ChangePasswordCommand(UUID userId, String currentPassword, String newPassword) {
	}
}
