package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when {@code CreateUserUseCase} is invoked for a {@code Person}
 * whose {@code institutionalEmail} is null (spec: "Rejects creation without
 * institutional email").
 */
public class MissingInstitutionalEmailException extends RuntimeException {

	public MissingInstitutionalEmailException(String message) {
		super(message);
	}
}
