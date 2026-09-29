package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Confirms the admission-ticket payment ({@code PENDING → PAID}) and moves the
 * {@code Candidate} to {@code PAID} (design invariant: no {@code PAID}
 * candidate without a paid {@code AdmissionPayment(concept=ADMISSION_FICHA)}).
 *
 * <p>TEMPORARY DEVIATION: today the trigger is the public portal's "Pagar en
 * línea" button (direct POST, no EVO payment verification). When the EVO
 * Hosted-Checkout / webhook integration lands, this use case becomes the
 * confirmation core invoked by it (and by the manual window capture) — the
 * aggregate already matches the design, only the entry point changes.
 */
public interface ConfirmAdmissionPaymentUseCase {

	ConfirmPaymentResult confirm(UUID candidateId);

	record ConfirmPaymentResult(UUID candidateId, String folio, CandidateStatus candidateStatus,
			String referenceNumber, BigDecimal amount, Instant paidAt, String receiptNumber) {
	}
}