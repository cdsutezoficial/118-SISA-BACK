package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Confirms the admission-ticket payment ({@code PENDING → PAID}) and moves the
 * {@code Candidate} to {@code PAID} (design invariant: no {@code PAID}
 * candidate without a settled ficha charge in finance).
 *
 * <p>TEMPORARY DEVIATION: today the trigger is the public portal's "Pagar en
 * línea" button (direct POST, no EVO payment verification). When the EVO
 * Hosted-Checkout / webhook integration lands, this use case becomes the
 * confirmation core invoked by it (and by the manual window capture) — the
 * aggregate already matches the design, only the entry point changes.
 */
public interface ConfirmAdmissionPaymentUseCase {

	/**
	 * Confirms the ficha paid by the checkout attempt {@code capturedOrderId}:
	 * the numbers booked are that attempt's snapshot — what EVO was asked for
	 * and captured — not whatever the ficha was re-quoted to afterwards.
	 *
	 * @param capturedOrderId the order the bank captured, or {@code null}
	 */
	ConfirmPaymentResult confirm(UUID candidateId, String capturedOrderId);

	/**
	 * What was paid, divided by charge (E6).
	 *
	 * @param amount          what was paid in this operation only (the abono), never the
	 *                        charge's total
	 * @param total           what the charge costs in full
	 * @param previouslyPaid  what the charge had already received before this operation:
	 *                        the money a career change carried over, or saldo a favor
	 * @param previousReceipt receipt of the earlier charge that money came from, when a
	 *                        career change carried it; {@code null} otherwise
	 * @param previousPaidAt  when that earlier charge was paid
	 * @param programChange   this was the difference a career change left to pay
	 */
	record ConfirmPaymentResult(UUID candidateId, String folio, CandidateStatus candidateStatus,
			String referenceNumber, BigDecimal amount, Instant paidAt, String receiptNumber, BigDecimal total,
			BigDecimal previouslyPaid, String previousReceipt, Instant previousPaidAt, boolean programChange) {

		/** A payment that settled its whole charge at once. */
		public ConfirmPaymentResult(UUID candidateId, String folio, CandidateStatus candidateStatus,
				String referenceNumber, BigDecimal amount, Instant paidAt, String receiptNumber) {
			this(candidateId, folio, candidateStatus, referenceNumber, amount, paidAt, receiptNumber, amount,
					BigDecimal.ZERO, null, null, false);
		}

		/** True when the charge had received money before this operation. */
		public boolean divided() {
			return previouslyPaid != null && previouslyPaid.signum() > 0;
		}
	}
}