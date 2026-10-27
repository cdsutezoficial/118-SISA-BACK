package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when an abono is attempted against a {@link mx.edu.utez.sisa.finance.domain.model.Payment}
 * whose status does not accept entries ({@code PAID}, {@code EXEMPT},
 * {@code CANCELLED}), or would exceed {@code amountDue}. Maps to HTTP 409.
 *
 * <p>Overpayment is rejected in Fase 1 rather than routed to a credit
 * ({@code PaymentCredit}, Fase 2): until saldo a favor exists, the honest
 * answer is "no" instead of money disappearing into an account nobody can
 * see.
 */
public class PaymentNotPayableException extends RuntimeException {

	public PaymentNotPayableException(String message) {
		super(message);
	}
}
