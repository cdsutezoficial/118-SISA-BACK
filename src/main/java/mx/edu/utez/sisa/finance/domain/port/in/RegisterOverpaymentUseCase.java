package mx.edu.utez.sisa.finance.domain.port.in;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Books as saldo a favor the money a gateway order captured without settling
 * anything: a second capture of a charge that was already paid, or a capture
 * on a charge that was cancelled. The money is real and is never returned in
 * cash (D3), so it stays with the charge's owner, tied to the order.
 *
 * <p>Idempotent by order: an order that already has its overpayment returns
 * that one, and an order that booked an abono (it is the one that paid the
 * charge) books nothing.
 */
public interface RegisterOverpaymentUseCase {

	/**
	 * @param originPaymentId the charge the order was opened for
	 * @param externalOrderId the gateway order that captured
	 * @param amount          what the gateway captured on it
	 * @return the overpayment for that order; empty when the order paid the
	 *         charge, so there is nothing left over
	 */
	Optional<Overpayment> register(UUID originPaymentId, String externalOrderId, BigDecimal amount);

	record Overpayment(UUID creditId, BigDecimal amount, boolean created) {
	}
}
