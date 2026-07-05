package mx.edu.utez.sisa.identity.domain.port.in;

/**
 * Validates local credentials and issues an access + refresh token pair
 * (spec: "Authenticate with Local Credentials"). Public — no caller gate;
 * implementations must reject {@code LOCKED} accounts and register failed
 * attempts via {@code User.registerFailedLogin()} on wrong passwords.
 */
public interface AuthenticateUseCase {

	AuthenticationResult authenticate(AuthenticateCommand command);

	record AuthenticateCommand(String username, String password) {
	}

	record AuthenticationResult(String accessToken, String refreshToken, boolean mustChangePassword) {
	}
}
