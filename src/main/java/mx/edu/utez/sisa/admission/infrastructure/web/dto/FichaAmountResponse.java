package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase;

import java.math.BigDecimal;

/**
 * Body for {@code GET /program-admission-configs/{id}/ficha-amount} — the ficha
 * price the registration wizard shows on its review step, taken from the
 * payment-concept catalog (Fase 11) instead of a hardcoded amount.
 *
 * @param amount      the amount the applicant must pay
 * @param currency    ISO currency of {@code amount}, so the portal never
 *                    hardcodes the symbol either
 * @param conceptName the catalog concept that prices the ficha
 * @param programName the program of the quoted admission config
 */
public record FichaAmountResponse(BigDecimal amount, String currency, String conceptName, String programName) {

	public static FichaAmountResponse from(GetFichaAmountUseCase.FichaAmountQuote quote) {
		return new FichaAmountResponse(quote.amount(), "MXN", quote.conceptName(), quote.programName());
	}
}
