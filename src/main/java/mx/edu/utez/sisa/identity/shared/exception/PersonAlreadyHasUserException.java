package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when {@code CreateUserUseCase} is invoked for a {@code Person}
 * that already has a {@code User} (spec: "Rejects duplicate account for the
 * same Person" — one-Person-one-User invariant).
 */
public class PersonAlreadyHasUserException extends RuntimeException {

	public PersonAlreadyHasUserException(String message) {
		super(message);
	}
}
