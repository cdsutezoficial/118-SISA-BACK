package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;

public record FinanceAlertResponse(UUID id, FinanceAlertType type, FinanceAlertStatus status, UUID candidateId,
		UUID paymentId, String orderId, String folio, BigDecimal amount, String detail, Instant createdAt,
		Instant lastSeenAt, Instant resolvedAt, UUID resolvedBy, String resolutionNote) {

	public static FinanceAlertResponse from(FinanceAlert alert) {
		return new FinanceAlertResponse(alert.getId(), alert.getType(), alert.getStatus(), alert.getCandidateId(),
				alert.getPaymentId(), alert.getOrderId(), alert.getFolio(), alert.getAmount(), alert.getDetail(),
				alert.getCreatedAt(), alert.getLastSeenAt(), alert.getResolvedAt(), alert.getResolvedBy(),
				alert.getResolutionNote());
	}
}
