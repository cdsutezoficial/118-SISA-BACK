package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;

import java.math.BigDecimal;

/**
 * Decisor de saldo (R7): given the effective {@code amountDue} and the abono
 * total, decides the adeudo's next status — {@code PENDING} (nothing paid),
 * {@code PARTIAL}, or {@code PAID}.
 *
 * <p>The doc states R7 as "amountPaid + descuento vs amountDue". That is the
 * same comparison this class makes, phrased the other way: the discount is
 * already inside {@code amountDue} (the quoter subtracted it), so comparing
 * the abono total against {@code amountDue} <em>is</em> comparing it against
 * "rate minus discount". Keeping the discount out of this class means the
 * decider stays a three-line rule with one job.
 *
 * <p>{@code EXEMPT} is deliberately absent from the outputs: a FULL benefit
 * produces a zero {@code amountDue} at creation time and the payment is born
 * {@code EXEMPT}; since {@code EXEMPT} does not accept abonos, no settlement
 * ever has to produce it.
 */
public class PaymentSettlementDecider {

	/**
	 * @param amountDue   the quoted, already-discounted amount
	 * @param amountPaid  the recomputed sum of the registered abonos
	 */
	public PaymentStatus decide(BigDecimal amountDue, BigDecimal amountPaid) {
		if (amountPaid.compareTo(amountDue) >= 0) {
			return PaymentStatus.PAID;
		}
		if (amountPaid.signum() > 0) {
			return PaymentStatus.PARTIAL;
		}
		return PaymentStatus.PENDING;
	}
}
