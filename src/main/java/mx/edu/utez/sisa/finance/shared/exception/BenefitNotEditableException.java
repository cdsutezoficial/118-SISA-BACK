package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when an edit or cancellation is attempted on a discount that is not
 * {@code ACTIVE} — an applied ({@code USED}), expired, or already cancelled
 * discount is immutable (Fase 2, pregunta 6). Maps to HTTP 409.
 */
public class BenefitNotEditableException extends RuntimeException {

	public BenefitNotEditableException(String message) {
		super(message);
	}
}
