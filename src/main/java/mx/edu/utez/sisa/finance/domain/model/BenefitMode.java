package mx.edu.utez.sisa.finance.domain.model;

/**
 * How a {@link PaymentBenefit} discount cuts the amount owed
 * (er-08-pagos.md {@code payment_benefit.mode}).
 *
 * <p>{@code FULL} is the 100 % case — an exemption. There is deliberately NO
 * {@code EXEMPTION} type: a 100 % discount is expressed as {@code FULL} so the
 * discount and its rastro are one mechanism instead of two (decisión
 * 2026-10-09). The quoted {@code amountDue} comes out as $0 and the payment is
 * created with {@link PaymentStatus#EXEMPT}.
 */
public enum BenefitMode {
	PERCENT,
	AMOUNT,
	FULL
}
