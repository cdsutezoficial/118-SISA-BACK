package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentEntry}. Append-only: there is no
 * update nor delete — an abono is history (R8).
 */
public interface PaymentEntryRepository {

	PaymentEntry save(PaymentEntry entry);

	List<PaymentEntry> findByPaymentIdOrderByPaidAtAsc(UUID paymentId);

	/**
	 * Sum of the registered abonos of a payment. Recomputed inside the
	 * settlement transaction rather than trusting {@code Payment.amountPaid},
	 * so two concurrent abonos cannot both read the same stale total.
	 */
	BigDecimal sumAmountByPaymentId(UUID paymentId);
}
