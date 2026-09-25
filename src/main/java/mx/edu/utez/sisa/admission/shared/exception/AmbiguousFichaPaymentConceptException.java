package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration finds MORE THAN ONE active
 * {@code ENROLLMENT} payment concept for the candidate's program — the ficha
 * amount is ambiguous and must never be picked silently (Fase 11: strict
 * resolution). Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler}.
 */
public class AmbiguousFichaPaymentConceptException extends RuntimeException {

	public AmbiguousFichaPaymentConceptException(String message) {
		super(message);
	}
}