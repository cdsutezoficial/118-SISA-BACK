package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a charge stands, without pricing it: the header a flow needs to show
 * a charge (reference, deadline, status) and, once money arrived, the receipt
 * of its last abono. The price is {@link QuoteDebtUseCase}'s business.
 */
public interface GetChargeStatusUseCase {

	/** Empty when the charge does not exist. */
	Optional<ChargeStatus> find(UUID paymentId);

	/**
	 * @param paidAt        when the last abono (or exemption entry) was booked;
	 *                      {@code null} while nothing was received
	 * @param receiptNumber the receipt of that entry
	 */
	record ChargeStatus(UUID paymentId, UUID candidateId, UUID studentId, String conceptCode,
			String referenceNumber, PaymentStatus status, Instant dueAt, Instant createdAt, BigDecimal amountPaid,
			Instant paidAt, String receiptNumber) {
	}
}
