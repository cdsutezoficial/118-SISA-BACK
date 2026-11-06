package mx.edu.utez.sisa.admission.shared.exception;

/**
 * There is already an online payment of this ficha that the bank has not
 * resolved, so a second session is not opened: both could be charged.
 * Responds 409.
 */
public class PaymentInProgressException extends RuntimeException {

	public PaymentInProgressException(String message) {
		super(message);
	}
}
