package mx.edu.utez.sisa.admission.shared.exception;

/**
 * F1: the amount the cash desk showed is no longer what the ficha owes (a discount or a
 * tariff changed between looking it up and collecting). Nothing was booked; the cashier
 * looks the ficha up again and collects the new amount. Maps to HTTP 409
 * {@code ADMISSION_CASH_AMOUNT_CHANGED}.
 */
public class CashAmountChangedException extends RuntimeException {

	public CashAmountChangedException(String message) {
		super(message);
	}
}
