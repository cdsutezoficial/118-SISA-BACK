package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Freezes the live quote of a charge onto the charge itself — the "photo"
 * taken at the exact moment the payer acts (opens the EVO checkout, pays at
 * caja).
 *
 * <p>The quote is computed again here, never taken from what a screen showed:
 * the tariff and the discount in force <em>now</em> are what get written
 * ({@code originalAmount}, {@code amountDue}, {@code benefitId},
 * {@code rateId}) and what the caller must charge. A charge whose discount
 * covers the whole tariff is left {@code EXEMPT}.
 *
 * <p>A charge that already carries abonos, or that is no longer open, is
 * refused: money that arrived cannot be re-priced.
 */
public interface FreezeDebtQuoteUseCase {

	DebtQuote freeze(UUID paymentId, LocalDate onDate);
}
