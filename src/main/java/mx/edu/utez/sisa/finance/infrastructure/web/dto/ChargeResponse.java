package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.ChargeView;

import java.math.BigDecimal;

/**
 * One charge on the "Aplicar descuento" screen: its live price, <b>the</b>
 * discount granted on it (at most one per charge — the screen edits it rather
 * than adding another), whether that discount can still be edited (not once
 * the charge is paid), and the saldo a favor it generated when the discount
 * came after payment.
 */
public record ChargeResponse(DebtQuoteResponse charge, BenefitDetailResponse discount, boolean discountEditable,
		BigDecimal creditAmount) {

	public static ChargeResponse from(ChargeView view, BenefitDetailResponse discount) {
		return new ChargeResponse(DebtQuoteResponse.from(view.quote()), discount, view.editable(),
				view.creditAmount());
	}
}
