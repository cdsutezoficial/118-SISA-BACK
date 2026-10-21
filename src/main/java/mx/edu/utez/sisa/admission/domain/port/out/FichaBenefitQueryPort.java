package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only port to the discount engine owned by the {@code finance} context
 * (Fase 2). The admission flow owns the ficha <em>rate</em> (the catalog
 * tariff, {@code FichaAmountResolver}) and hands it here to learn what the
 * applicant's discount does to it — finance owns the discount rules and must
 * stay the single place they live.
 *
 * <p>Implemented by an adapter that calls {@code finance}'s
 * {@code QuoteBenefitUseCase}; the {@code admission} domain stays ignorant of
 * {@code PaymentBenefit}, its repository, and how a percent is rounded.
 */
public interface FichaBenefitQueryPort {

	/**
	 * @param candidateId the applicant's candidate (ficha discounts are keyed by
	 *                    candidate since the 2026-10-21 reanchor); may be
	 *                    {@code null} for a preview with no candidate yet
	 * @param conceptCode the ficha concept the discount is scoped to
	 *                    ({@code ADMISSION_FICHA})
	 * @param rateAmount the live catalog tariff, before any discount
	 * @param onDate     the business date the discount window is evaluated against
	 */
	FichaDiscount resolve(UUID candidateId, String conceptCode, BigDecimal rateAmount, LocalDate onDate);

	/**
	 * @param originalAmount the rate, rounded
	 * @param discountAmount the discount applied ({@code 0} when none)
	 * @param amountDue      what the applicant must actually pay
	 * @param benefitId      the applied discount's id, or {@code null} when none —
	 *                       frozen onto the ficha so the exemption keeps its rastro
	 * @param exempt         {@code true} when a FULL discount covered the whole
	 *                       rate and nothing is owed
	 */
	record FichaDiscount(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue, UUID benefitId,
			boolean exempt) {

		/** The pass-through used when the applicant has no discount in force. */
		public static FichaDiscount none(BigDecimal rateAmount) {
			BigDecimal rate = rateAmount.setScale(2, java.math.RoundingMode.HALF_UP);
			return new FichaDiscount(rate, BigDecimal.ZERO.setScale(2), rate, null, rate.signum() == 0);
		}
	}
}
