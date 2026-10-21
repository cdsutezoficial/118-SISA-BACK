package mx.edu.utez.sisa.finance.domain.model;

/**
 * Lifecycle of a {@link PaymentBenefit} (discount).
 *
 * <p>{@code ACTIVE} is in force and may be resolved at quoting time;
 * {@code USED} marks a discount already consumed by a payment; {@code CANCELLED}
 * voids it; {@code EXPIRED} is the lapsed-by-date state. Expiry is also
 * enforced at resolution time by the {@code validFrom/validTo} window, so a
 * row can sit {@code ACTIVE} past its window and simply never resolve — the
 * status is bookkeeping, the window is the rule.
 */
public enum BenefitStatus {
	ACTIVE,
	USED,
	CANCELLED,
	EXPIRED
}
