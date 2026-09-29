package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the payment-confirmation flow targets a candidate id that does
 * not exist (or whose ficha payment is missing). Maps to HTTP 404.
 */
public class CandidateNotFoundException extends RuntimeException {

	public CandidateNotFoundException(String message) {
		super(message);
	}
}