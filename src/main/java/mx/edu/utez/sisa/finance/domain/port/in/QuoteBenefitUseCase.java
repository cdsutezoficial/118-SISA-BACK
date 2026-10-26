package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cross-context quoting of a discount for another bounded context
 * ({@code admission} quoting the ficha, and in the future any other module that
 * sells a concept finance owns the discounts for).
 *
 * <p>Deliberately separate from {@link QuotePaymentUseCase}: that one resolves
 * the concept and its catalog rate so finance can freeze an adeudo of its own,
 * while this one is a pure "apply the person's discount to an amount I already
 * know" — the caller owns the rate (the admission tariff) and only asks finance
 * which discount is in force and what the numbers become. It exists so the
 * discount rules (window, anchor, mode, rounding) live in ONE place and the
 * ficha cannot price a discount differently than any other concept.
 *
 * <p>Read-only. Nothing is persisted, and the benefit is not marked used: a
 * discount stays re-evaluable until a payment actually freezes it (R8).
 */
public interface QuoteBenefitUseCase {

	BenefitQuote quote(BenefitQuoteCommand command);

	/**
	 * @param candidateId   aspirante anchor — exactly one of
	 *                      {@code candidateId}/{@code studentId} is required
	 * @param studentId     student anchor
	 * @param conceptCode   required — the concept the discount is scoped to
	 *                      (e.g. {@code ADMISSION_FICHA})
	 * @param periodId      optional — charge anchor; {@code null} for fichas
	 * @param generationId  optional — charge anchor
	 * @param rateAmount    required — the caller's own catalog rate, before any
	 *                      discount
	 * @param onDate        required — business date the window is evaluated against
	 */
	record BenefitQuoteCommand(UUID candidateId, UUID studentId, String conceptCode, UUID periodId, UUID generationId,
			BigDecimal rateAmount, LocalDate onDate) {
	}

	/**
	 * @param originalAmount the caller's rate, rounded
	 * @param discountAmount the discount applied (0 when none)
	 * @param amountDue      {@code originalAmount - discountAmount}
	 * @param benefitId      the applied discount's id, or {@code null} when none
	 * @param mode           the applied discount's mode, or {@code null}
	 * @param exempt         {@code true} when {@code amountDue} is zero because a
	 *                       {@link BenefitMode#FULL} benefit covered the whole rate
	 */
	record BenefitQuote(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue, UUID benefitId,
			BenefitMode mode, boolean exempt) {
	}
}
