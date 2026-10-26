package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;

import java.time.LocalDate;

/**
 * Projects a {@link PaymentBenefit} into the read-side {@link BenefitView}
 * shared by the whole Fase 2 CRUD — one mapping, so create/update/cancel/list
 * and the report never disagree on what a discount looks like.
 */
public final class BenefitViews {

	private BenefitViews() {
	}

	/**
	 * @param today the business date used to compute {@code inForce}: the
	 *              discount is in force when it is {@code ACTIVE} and today is
	 *              inside its window.
	 */
	public static BenefitView toView(PaymentBenefit benefit, LocalDate today) {
		boolean inForce = benefit.isActive() && !today.isBefore(benefit.getValidFrom())
				&& (benefit.getValidTo() == null || !today.isAfter(benefit.getValidTo()));
		return new BenefitView(benefit.getId(), benefit.getCandidateId(), benefit.getStudentId(),
				benefit.getConceptCode(), benefit.getPeriodId(), benefit.getGenerationId(), benefit.getMode(),
				benefit.getPercent(), benefit.getAmount(), benefit.getValidFrom(), benefit.getValidTo(),
				benefit.getStatus(), benefit.getReason(), benefit.getGrantedById(), benefit.getGrantedByName(),
				benefit.getCreatedAt(), inForce, benefit.getPaymentId());
	}
}
