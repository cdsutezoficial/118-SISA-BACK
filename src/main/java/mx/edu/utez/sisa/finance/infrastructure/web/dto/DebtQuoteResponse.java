package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A charge with its price computed live: "cuesta {@code originalAmount} −
 * descuento {@code discountAmount} = total {@code amountDue}". Nothing here is
 * stored while the charge is open ({@code live = true}); closed charges report
 * the numbers they were settled with ({@code live = false}).
 */
public record DebtQuoteResponse(UUID paymentId, UUID candidateId, UUID studentId, String conceptCode,
		String conceptName, PaymentStatus status, BigDecimal originalAmount, BigDecimal discountAmount,
		BigDecimal amountDue, BigDecimal amountPaid, UUID benefitId, BenefitMode benefitMode, BigDecimal benefitPercent,
		BigDecimal benefitAmount, String benefitReason, boolean exempt, boolean live, Instant dueAt,
		String pricingError) {

	public static DebtQuoteResponse from(DebtQuote quote) {
		return new DebtQuoteResponse(quote.paymentId(), quote.candidateId(), quote.studentId(), quote.conceptCode(),
				quote.conceptName(), quote.status(), quote.originalAmount(), quote.discountAmount(), quote.amountDue(),
				quote.amountPaid(), quote.benefitId(), quote.benefit() == null ? null : quote.benefit().mode(),
				quote.benefit() == null ? null : quote.benefit().percent(),
				quote.benefit() == null ? null : quote.benefit().amount(),
				quote.benefit() == null ? null : quote.benefit().reason(), quote.exempt(), quote.live(),
				quote.dueAt(), quote.pricingError());
	}
}
