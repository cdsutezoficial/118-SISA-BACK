package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /finance/payments/{id}/apply-credit} response: the adeudo after
 * the application, how much saldo a favor was consumed, and which credits were
 * touched — so the screen can show "se aplicaron $X de tu saldo a favor".
 */
public record CreditApplicationResponse(PaymentResponse payment, BigDecimal creditApplied,
		List<UUID> appliedCreditIds) {
}
