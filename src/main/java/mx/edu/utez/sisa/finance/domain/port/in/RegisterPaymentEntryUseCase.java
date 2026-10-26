package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentGatewayStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registers an abono against a {@code Payment} and runs the settlement
 * decider (R7): {@code PENDING → PARTIAL → PAID}. The abono never touches a
 * {@code PaymentBenefit} (R8).
 */
public interface RegisterPaymentEntryUseCase {

	CreatePaymentUseCase.PaymentResult registerEntry(RegisterEntryCommand command);

	/**
	 * @param amount     required, positive, never above the remaining balance
	 * @param method     required — {@code CASH} / {@code ONLINE} / {@code CARD}
	 * @param channel    required — {@code PORTAL} / {@code CASHIER} / {@code LINK}
	 * @param gateway    optional — {@code EVO} or {@code null} for cash
	 * @param externalOrderId optional — gateway order id
	 * @param linkId     optional — payment link (Fase 4)
	 * @param reference  optional — reference/comprobante
	 * @param receiptNumber optional — {@code REC-…} number
	 * @param isPhysical {@code true} when collected at the ventanilla (corte)
	 * @param collectedById / collectedByName optional — who collected it
	 * @param gatewayStatus optional — online charge outcome
	 */
	record RegisterEntryCommand(UUID paymentId, BigDecimal amount, PaymentEntryMethod method, PaymentChannel channel,
			String gateway, String externalOrderId, UUID linkId, String reference, String receiptNumber,
			boolean isPhysical, UUID collectedById, String collectedByName, PaymentGatewayStatus gatewayStatus) {
	}
}
