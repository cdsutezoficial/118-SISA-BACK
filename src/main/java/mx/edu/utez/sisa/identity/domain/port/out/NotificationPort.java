package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for sending emails (01-identidad.md — NotificationPort). Backs both
 * halves of the credential-recovery story: {@link #sendPasswordReset} delivers
 * the self-service single-use reset link to the user's institutional email,
 * and {@link #sendTemporaryPassword} delivers the temporary password an ADMIN
 * assigned because the user could not get in at all.
 */
public interface NotificationPort {

	void sendPasswordReset(String to, String resetLink);

	/**
	 * @param to               recipient's institutional email (the {@code User} username)
	 * @param temporaryPassword the plaintext password just assigned, in
	 *                         {@code AdminResetPasswordUseCase} order to be
	 *                         delivered to its holder — it is never persisted
	 *                         or logged in plaintext
	 */
	void sendTemporaryPassword(String to, String temporaryPassword);
}