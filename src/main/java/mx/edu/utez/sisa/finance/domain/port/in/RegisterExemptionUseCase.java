package mx.edu.utez.sisa.finance.domain.port.in;

import java.util.UUID;

/**
 * Registers the {@code $0} abono that gives a FULL-benefit (100 %) exemption
 * its rastro (R4, Fase 2 §5.1): an {@code EXEMPT} adeudo already carries
 * {@code amountDue = $0} and the {@code benefitId}, and this adds the receipt
 * entry tied to that discount so the report and the corte de caja see "pagado".
 * An {@code EXEMPT} payment does not accept normal abonos
 * ({@code PaymentStatus#acceptsEntries()}), which is exactly why this is its
 * own use case.
 */
public interface RegisterExemptionUseCase {

	CreatePaymentUseCase.PaymentResult registerExemption(RegisterExemptionCommand command);

	/**
	 * @param paymentId       required — the {@code EXEMPT} adeudo
	 * @param receiptNumber   optional — {@code REC-…} printed on the receipt
	 * @param reference       optional — free-text note
	 * @param collectedById   required — the acting user (from the JWT)
	 * @param collectedByName optional — display name for the receipt
	 */
	record RegisterExemptionCommand(UUID paymentId, String receiptNumber, String reference, UUID collectedById,
			String collectedByName) {
	}
}
