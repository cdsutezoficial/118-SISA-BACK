package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when username/password validation fails during authentication
 * (spec: "Authenticate with Local Credentials").
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException(String message) {
		super(message);
	}
}
