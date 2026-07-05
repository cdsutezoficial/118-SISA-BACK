package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a use case is invoked for a {@code User} id that does not
 * resolve to an existing account (e.g. {@code AssignRoleUseCase} target).
 */
public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(String message) {
		super(message);
	}
}
