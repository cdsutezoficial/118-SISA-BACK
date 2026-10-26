package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;

import java.util.UUID;

/**
 * Marks a discount as consumed ({@code USED}) once the charge it priced is
 * settled. Shared by the abono and the exemption paths so both close the
 * discount the same way.
 */
final class BenefitUsage {

	private BenefitUsage() {
	}

	/**
	 * @return the benefit after the transition, or {@code null} when it no
	 *         longer exists
	 */
	static PaymentBenefit markUsed(PaymentBenefitRepository repository, UUID benefitId) {
		PaymentBenefit benefit = repository.findById(benefitId).orElse(null);
		if (benefit == null) {
			return null;
		}
		if (benefit.isActive()) {
			benefit.markUsed();
			PaymentBenefit saved = repository.save(benefit);
			return saved != null ? saved : benefit;
		}
		return benefit;
	}
}
