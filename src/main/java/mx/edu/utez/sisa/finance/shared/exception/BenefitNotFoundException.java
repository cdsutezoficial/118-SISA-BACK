package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a {@code PaymentBenefit} id does not resolve to an existing
 * discount. Maps to HTTP 404 in {@code finance}'s {@code GlobalExceptionHandler}
 * — same convention as {@code PaymentNotFoundException}.
 */
public class BenefitNotFoundException extends RuntimeException {

	public BenefitNotFoundException(String message) {
		super(message);
	}
}
