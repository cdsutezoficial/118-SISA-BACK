package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a discount payload is structurally invalid (missing person,
 * blank concept, mode-specific fields absent, invalid window…). Maps to HTTP
 * 400 — same convention as {@code InvalidPaymentDataException}.
 */
public class InvalidBenefitDataException extends RuntimeException {

	public InvalidBenefitDataException(String message) {
		super(message);
	}
}
