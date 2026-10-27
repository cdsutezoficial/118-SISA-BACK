package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a {@code conceptCode} does not resolve to an existing
 * {@code PaymentConcept}. Maps to HTTP 404.
 */
public class ConceptNotFoundException extends RuntimeException {

	public ConceptNotFoundException(String message) {
		super(message);
	}
}
