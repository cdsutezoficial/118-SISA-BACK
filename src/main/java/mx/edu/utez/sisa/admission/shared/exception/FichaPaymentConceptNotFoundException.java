package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration finds NO active {@code ENROLLMENT}
 * payment concept for the candidate's program — the ficha amount cannot be
 * resolved (Fase 11: strict resolution, no config fallback). Maps to HTTP 409
 * in the web layer's {@code GlobalExceptionHandler}.
 */
public class FichaPaymentConceptNotFoundException extends RuntimeException {

	public FichaPaymentConceptNotFoundException(String message) {
		super(message);
	}
}