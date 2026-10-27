package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A saldo a favor as rendered by {@code GET /finance/credits}.
 */
public record CreditResponse(UUID id, UUID candidateId, UUID studentId, UUID originPaymentId, UUID benefitId,
		BigDecimal amount, CreditStatus status, UUID appliedToPaymentId, Instant createdAt) {
}
