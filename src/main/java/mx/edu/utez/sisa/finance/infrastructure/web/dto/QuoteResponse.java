package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.math.BigDecimal;

/**
 * Result of {@code GET /finance/quote}: the amounts the quoter resolved, the
 * discount applied (when any), and whether the charge is exempt.
 */
public record QuoteResponse(PaymentConceptType conceptType, BigDecimal originalAmount, BigDecimal discountAmount,
		BigDecimal amountDue, BenefitResponse benefit, boolean exempt) {
}
