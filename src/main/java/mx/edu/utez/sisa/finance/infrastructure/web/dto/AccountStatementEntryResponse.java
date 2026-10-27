package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One abono in the account statement.
 */
public record AccountStatementEntryResponse(UUID id, BigDecimal amount, PaymentEntryMethod method,
		PaymentChannel channel, String receiptNumber, String reference, boolean isPhysical, String collectedByName,
		Instant paidAt) {
}
