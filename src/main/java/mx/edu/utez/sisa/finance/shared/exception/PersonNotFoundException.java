package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a {@code personId} does not resolve to an existing
 * {@code Person}. Maps to HTTP 404.
 */
public class PersonNotFoundException extends RuntimeException {

	public PersonNotFoundException(String message) {
		super(message);
	}
}
