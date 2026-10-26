package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a payment-creation payload is structurally invalid (blank
 * concept, missing person, invalid origin, negative amount…). Maps to HTTP
 * 400 — same convention as {@code InvalidPaymentConceptDataException}.
 */
public class InvalidPaymentDataException extends RuntimeException {

	public InvalidPaymentDataException(String message) {
		super(message);
	}
}
