package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cotizador puro (R3): from a catalog rate and the optional discount in
 * force, produces the three amounts an adeudo freezes —
 * {@code originalAmount}, {@code discountAmount}, {@code amountDue}.
 *
 * <p>MXN is fixed here and nowhere else: 2 decimal places, {@code HALF_UP}.
 * Every amount that leaves this class is already rounded, so downstream code
 * (the decider, the statement, the receipts) never re-rounds and two of them
 * can never disagree by a cent.
 *
 * <p>{@link BenefitMode#FULL} zeroes the whole rate — the exemption case. A
 * fixed {@link BenefitMode#AMOUNT} discount larger than the rate is capped at
 * the rate rather than producing a negative {@code amountDue}: a discount may
 * cover everything, but it never invents money in this phase (saldo a favor
 * is Fase 2).
 */
public class PaymentQuoter {

	public static final int SCALE = 2;

	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	/**
	 * @param rateAmount required — the ACTIVE {@code PaymentRate} amount for
	 *                   the concept/program, already resolved by the caller
	 * @param benefit    optional — the discount in force, already filtered by
	 *                   window and anchor ({@link BenefitResolver})
	 */
	public Quotation quote(BigDecimal rateAmount, PaymentBenefit benefit) {
		BigDecimal original = round(rateAmount);
		if (benefit == null) {
			return new Quotation(original, zero(), original, null, false);
		}
		BigDecimal discount = switch (benefit.getMode()) {
			case FULL -> original;
			case PERCENT -> round(original.multiply(benefit.getPercent()).divide(new BigDecimal("100")));
			case AMOUNT -> round(benefit.getAmount().min(original));
		};
		BigDecimal due = original.subtract(discount);
		return new Quotation(original, discount, due, benefit, due.signum() == 0);
	}

	public static BigDecimal round(BigDecimal amount) {
		return amount.setScale(SCALE, ROUNDING);
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(SCALE);
	}

	/**
	 * @param benefit {@code null} when no discount applied
	 * @param exempt  {@code true} when {@code due} is zero because the benefit
	 *                covered the whole rate
	 */
	record Quotation(BigDecimal original, BigDecimal discount, BigDecimal due, PaymentBenefit benefit, boolean exempt) {
	}
}
