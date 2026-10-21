package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks the discount that governs a quote (R5/R6).
 *
 * <p>Receives the {@code ACTIVE} candidates for the person/concept (the
 * repository already excluded non-ACTIVE rows) and applies the two rules the
 * entity itself carries: the {@code validFrom/validTo} window and the
 * {@code periodId/generationId} anchor
 * ({@link PaymentBenefit#appliesTo}). An expired, future, or unanchored
 * discount simply does not resolve — the quote falls back to the full rate,
 * which is R6.
 *
 * <p>When several discounts qualify, the most anchored wins (both anchors,
 * then one, then none); ties break on the newest {@code createdAt}. One
 * benefit per payment is the Fase 1 rule — stacking is out of scope until the
 * catalog says otherwise.
 */
public class BenefitResolver {

	public Optional<PaymentBenefit> resolve(List<PaymentBenefit> candidates, LocalDate onDate, UUID periodId,
			UUID generationId) {
		return candidates.stream()
				.filter(benefit -> benefit.appliesTo(onDate, periodId, generationId))
				.min(Comparator.comparingInt(BenefitResolver::anchorDistance)
						.thenComparing(PaymentBenefit::getCreatedAt, Comparator.reverseOrder()));
	}

	/**
	 * How many of the two anchors the benefit leaves open — 0 (both pinned),
	 * 1, or 2 (applies to any period/generation). Lower wins.
	 */
	private static int anchorDistance(PaymentBenefit benefit) {
		int distance = 0;
		if (benefit.getPeriodId() == null) {
			distance++;
		}
		if (benefit.getGenerationId() == null) {
			distance++;
		}
		return distance;
	}
}
