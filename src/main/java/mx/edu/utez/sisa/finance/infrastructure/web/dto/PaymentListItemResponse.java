package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of the paginated adeudo list ({@code GET /finance/payments}); the
 * applied discount is read from the detail endpoint, not the list.
 */
public record PaymentListItemResponse(UUID id, UUID candidateId, UUID studentId, String conceptCode,
		BigDecimal originalAmount, BigDecimal amountDue, BigDecimal amountPaid, String status, String origin,
		Instant dueAt, Instant createdAt) {
}
