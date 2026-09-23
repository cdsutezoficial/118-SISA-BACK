package mx.edu.utez.sisa.identity.domain.port.in;

/**
 * Completes the "forgot my password" flow (01-identidad.md —
 * ResetPasswordUseCase): validates the presented token (exists, not used, not
 * expired), applies the new password, and marks the token consumed. Rejects the
 * request (no password change) when any of those checks fail.
 */
public interface ResetPasswordUseCase {

	void reset(ResetPasswordCommand command);

	/**
	 * @param token       the plaintext token from the emailed reset link
	 * @param newPassword the new password, hashed by the implementation
	 */
	record ResetPasswordCommand(String token, String newPassword) {
	}
}