package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Books the money that settles a charge, with the numbers the payer was
 * actually charged.
 *
 * <p>The caller hands in the snapshot it charged with (the one sent to EVO
 * for that checkout attempt, or the one caja showed). If the charge was
 * frozen again afterwards — a second checkout opened with a different
 * discount — the snapshot of the attempt that actually captured wins and is
 * written back, so the booked amount is always the amount that moved.
 * Without a snapshot the live quote is frozen first.
 *
 * <p>An {@code EXEMPT} charge receives its single {@code $0} exemption entry.
 * The discount that priced the charge is marked {@code USED}.
 * Idempotent: settling a {@code PAID} charge is a no-op.
 */
public interface SettleDebtUseCase {

	SettledDebt settle(SettleDebtCommand command);

	/**
	 * @param paymentId       the charge
	 * @param snapshot        what the payer was charged, or {@code null} to
	 *                        freeze the live quote now
	 * @param method          {@code ONLINE} or {@code CASH}
	 * @param channel         where the money came in
	 * @param gateway         {@code EVO} or {@code null}
	 * @param externalOrderId the gateway order, when there is one
	 * @param receiptNumber   the receipt issued
	 * @param reference       optional reference
	 * @param collectedById   optional acting user
	 * @param collectedByName optional display name
	 */
	record SettleDebtCommand(UUID paymentId, ChargedSnapshot snapshot, PaymentEntryMethod method,
			PaymentChannel channel, String gateway, String externalOrderId, String receiptNumber, String reference,
			UUID collectedById, String collectedByName) {
	}

	/**
	 * The numbers a payer was charged with.
	 */
	record ChargedSnapshot(UUID rateId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue,
			UUID benefitId) {
	}

	/**
	 * @param amountPaid what the charge has received in total after settling
	 * @param amountDue  what the charge was settled for
	 */
	record SettledDebt(UUID paymentId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue,
			BigDecimal amountPaid, UUID benefitId, boolean exempt) {
	}
}
