package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a discount that arrives <b>after</b> a payment was made into saldo a
 * favor (R12, Fase 2 §7.3). For every already-{@code PAID} adeudo of the same
 * candidato/estudiante on the same {@code concepto} that this discount would
 * have cheapened, the overpayment ({@code amountPaid − amountDue-with-discount})
 * becomes a {@link PaymentCredit} <b>of the same anchor</b>, ready to be applied
 * to any concept.
 *
 * <p>Kept separate from {@code CreateBenefitUseCaseImpl} so the "grant" write
 * and the "refund as credit" write are two named things, and the retroactive
 * rule can be tested on its own. A payment that already carried this very
 * discount is skipped, so re-saving a discount cannot mint credit twice, and
 * so is a paid charge that a career change replaced (its money already moved).
 */
public class RetroactiveDiscountGranter {

	private final PaymentRepository paymentRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	private final PaymentQuoter paymentQuoter;

	private final ReplacedChargeGuard replacedChargeGuard;

	public RetroactiveDiscountGranter(PaymentRepository paymentRepository,
			PaymentCreditRepository paymentCreditRepository, PaymentQuoter paymentQuoter,
			ReplacedChargeGuard replacedChargeGuard) {
		this.paymentRepository = paymentRepository;
		this.paymentCreditRepository = paymentCreditRepository;
		this.paymentQuoter = paymentQuoter;
		this.replacedChargeGuard = replacedChargeGuard;
	}

	/**
	 * @return the credits created, oldest payment first; empty when nothing was
	 *         overpaid
	 */
	public List<PaymentCredit> grant(PaymentBenefit benefit, Instant now) {
		// A discount granted on one charge only affects that charge.
		List<Payment> candidates = benefit.getPaymentId() != null
				? paymentRepository.findById(benefit.getPaymentId()).map(List::of).orElse(List.of())
				: benefit.getCandidateId() != null
						? paymentRepository.findByCandidateIdAndConceptCode(benefit.getCandidateId(),
								benefit.getConceptCode())
						: List.of();
		List<PaymentCredit> credits = new ArrayList<>();
		for (Payment payment : candidates) {
			if (payment.getStatus() != PaymentStatus.PAID) {
				continue;
			}
			// A header-only ficha has no frozen tariff to re-quote yet.
			if (payment.getOriginalAmount() == null || payment.getAmountPaid() == null) {
				continue;
			}
			if (benefit.getId() != null && benefit.getId().equals(payment.getBenefitId())) {
				continue;
			}
			// A paid charge a career change replaced already handed its money to
			// the new charge; discounting it again would pay that money twice.
			if (replacedChargeGuard.isReplaced(payment.getId())) {
				continue;
			}
			BigDecimal newDue = paymentQuoter.quote(payment.getOriginalAmount(), benefit).due();
			// What the charge still keeps: an earlier discount already lowered its
			// amount due and returned the rest as saldo, which cannot go back twice.
			BigDecimal kept = Payment.moneyKept(payment);
			BigDecimal overpaid = kept.subtract(newDue);
			if (overpaid.signum() <= 0) {
				continue;
			}
			payment.applyRetroactiveDiscount(benefit.getId(), newDue);
			paymentRepository.save(payment);
			credits.add(paymentCreditRepository.save(new PaymentCredit(payment.getCandidateId(),
					payment.getStudentId(), payment.getId(), benefit.getId(), overpaid, now)));
		}
		return credits;
	}

}
