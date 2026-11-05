package mx.edu.utez.sisa.admission.domain.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * F1: the cash desk ({@code CAJA}) collects a ficha, or the difference a career change
 * left, in cash.
 *
 * <p>Two steps, because the money is physical. {@link #preview} runs every check that can
 * refuse (window, place, an online session still open, a 100 % discount) <em>before</em>
 * the cashier takes the money, and says how much to take. {@link #collect} runs them again
 * and books the payment; if the amount changed in between it books nothing.
 */
public interface CollectCashPaymentUseCase {

	CashPaymentPreview preview(UUID candidateId);

	ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult collect(UUID candidateId, BigDecimal expectedAmount,
			UUID collectorId);

	/**
	 * @param programChange the money is the difference a career change left, not the ficha
	 * @param total         what the charge costs in full
	 * @param alreadyPaid   what it has received (saldo a favor, or what the earlier career's
	 *                      ficha carried)
	 * @param toCollect     what the cashier takes now
	 * @param dueAt         the last day it can be paid, when it has one
	 */
	record CashPaymentPreview(UUID candidateId, String folio, String referenceNumber, boolean programChange,
			BigDecimal total, BigDecimal alreadyPaid, BigDecimal toCollect, Instant dueAt) {
	}
}
