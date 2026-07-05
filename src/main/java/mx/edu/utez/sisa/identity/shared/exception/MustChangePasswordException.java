package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a {@code User} with {@code mustChangePassword = true} attempts
 * any operation other than {@code ChangePasswordUseCase} (spec: "Mandatory
 * First-Access Password Change" — every operation is rejected while the flag
 * is pending).
 */
public class MustChangePasswordException extends RuntimeException {

	public MustChangePasswordException(String message) {
		super(message);
	}
}
