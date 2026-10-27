package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;

/**
 * Projects a {@link PaymentBenefit} into the read-side
 * {@link BenefitSummary} shared by the quote, the payment result, and the
 * account statement — one mapping, so the three never disagree on what a
 * discount looks like.
 */
public final class BenefitSummaries {

	private BenefitSummaries() {
	}

	/**
	 * @return {@code null} when there is no benefit, so {@code null} keeps
	 *         meaning "no discount" all the way to the response.
	 */
	public static BenefitSummary from(PaymentBenefit benefit) {
		if (benefit == null) {
			return null;
		}
		return new BenefitSummary(benefit.getId(), benefit.getMode(), benefit.getPercent(), benefit.getAmount(),
				benefit.getReason(), benefit.getGrantedByName());
	}
}
