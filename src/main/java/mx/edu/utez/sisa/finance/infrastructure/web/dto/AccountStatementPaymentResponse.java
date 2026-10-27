package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One adeudo with its abonos in the account statement ({@code GET
 * /finance/account-statement}). {@code balance} is computed
 * ({@code amountDue - amountPaid}).
 */
public record AccountStatementPaymentResponse(UUID id, String conceptCode, BigDecimal originalAmount,
		BigDecimal amountDue, BigDecimal amountPaid, BigDecimal balance, PaymentStatus status, String origin,
		Instant dueAt, Instant createdAt, BenefitResponse benefit, List<AccountStatementEntryResponse> entries) {
}
