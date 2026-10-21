package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentSnapshotStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Single adeudo as returned by create / register-entry. The applied discount
 * is carried so the caller sees the same snapshot the quoter produced.
 */
public record PaymentResponse(UUID id, UUID candidateId, UUID studentId, String conceptCode, BigDecimal originalAmount,
		BigDecimal amountDue, BigDecimal amountPaid, PaymentSnapshotStatus status, String origin, Instant dueAt,
		Instant createdAt, BenefitResponse benefit) {
}
