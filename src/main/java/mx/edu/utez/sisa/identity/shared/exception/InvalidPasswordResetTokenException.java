package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a presented password-reset token is unknown, already used, or
 * expired (01-identidad.md — ResetPasswordUseCase). The account password is
 * left unchanged.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

	public InvalidPasswordResetTokenException(String message) {
		super(message);
	}
}