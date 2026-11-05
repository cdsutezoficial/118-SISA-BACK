package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Opens a Finanzas alert for a payment that needs a person (E4). Best effort: a
 * failure is logged and never stops the payment flow that found the condition.
 */
public interface FinanceAlertPort {

	void raise(Kind kind, UUID candidateId, UUID paymentId, String orderId, BigDecimal amount, String detail);

	enum Kind {
		/** Authorized at the bank for more than three days and never captured. */
		AUTHORIZED_NOT_CAPTURED,
		/** Captured after the hold, with the career already full: kept as saldo a favor. */
		PAID_WITHOUT_QUOTA,
		/** Captured without settling anything: kept as saldo a favor. */
		OVERPAYMENT
	}
}
