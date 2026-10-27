package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a {@code Payment} id does not resolve to an existing adeudo.
 * Maps to HTTP 404 in {@code finance}'s {@code GlobalExceptionHandler} — same
 * convention as {@code PaymentConceptNotFoundException}.
 */
public class PaymentNotFoundException extends RuntimeException {

	public PaymentNotFoundException(String message) {
		super(message);
	}
}
