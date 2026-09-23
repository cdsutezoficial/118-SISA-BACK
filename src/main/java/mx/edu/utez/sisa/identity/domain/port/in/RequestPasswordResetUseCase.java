package mx.edu.utez.sisa.identity.domain.port.in;

/**
 * Starts the "forgot my password" flow (01-identidad.md —
 * RequestPasswordResetUseCase): generates a single-use token, persists only its
 * SHA-256 hash, invalidates any previous tokens of the same user, and sends the
 * reset link to the user's institutional email. The plaintext token is never
 * stored.
 */
public interface RequestPasswordResetUseCase {

	void request(RequestPasswordResetCommand command);

	/**
	 * @param username the account's institutional email ({@code User.username})
	 */
	record RequestPasswordResetCommand(String username) {
	}
}