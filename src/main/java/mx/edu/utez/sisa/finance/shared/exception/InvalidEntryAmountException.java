package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when an abono amount is not positive. Maps to HTTP 400. A zero or
 * negative abono would either do nothing while still writing a row, or
 * silently shrink {@code amountPaid} — neither is a payment.
 */
public class InvalidEntryAmountException extends RuntimeException {

	public InvalidEntryAmountException(String message) {
		super(message);
	}
}
