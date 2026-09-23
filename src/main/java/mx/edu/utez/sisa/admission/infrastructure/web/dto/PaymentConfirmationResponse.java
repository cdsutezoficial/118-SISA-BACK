package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Body for {@code POST /candidates/{id}/payments/confirm} — the paid-ficha
 * confirmation payload the portal's "Pagar en línea" screen consumes
 * (candidate {@code PAID}, receipt number, paid instant). Projection of
 * {@code ConfirmPaymentResult}.
 */
public record PaymentConfirmationResponse(UUID candidateId, String folio, CandidateStatus candidateStatus,
		String referenceNumber, BigDecimal amount, Instant paidAt, String receiptNumber) {

	public static PaymentConfirmationResponse from(ConfirmPaymentResult result) {
		return new PaymentConfirmationResponse(result.candidateId(), result.folio(), result.candidateStatus(),
				result.referenceNumber(), result.amount(), result.paidAt(), result.receiptNumber());
	}
}