package mx.edu.utez.sisa.finance.shared.exception;

/** The Finanzas alert does not exist. Maps to HTTP 404. */
public class FinanceAlertNotFoundException extends RuntimeException {

	public FinanceAlertNotFoundException(String message) {
		super(message);
	}
}
