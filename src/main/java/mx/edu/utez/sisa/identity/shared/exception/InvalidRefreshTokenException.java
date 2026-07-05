package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a presented refresh token is missing, expired, or revoked
 * (spec: "Exchange Refresh Token for New Access Token").
 */
public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException(String message) {
		super(message);
	}
}
