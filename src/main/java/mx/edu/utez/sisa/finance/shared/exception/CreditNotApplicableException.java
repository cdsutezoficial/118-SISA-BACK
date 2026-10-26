package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when a saldo a favor (credit) cannot be applied: the adeudo does not
 * accept abonos, has no balance, or the credit is not {@code AVAILABLE}. Maps
 * to HTTP 409 — same convention as {@code PaymentNotPayableException}.
 */
public class CreditNotApplicableException extends RuntimeException {

	public CreditNotApplicableException(String message) {
		super(message);
	}
}
