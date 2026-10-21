package mx.edu.utez.sisa.finance.domain.model;

/**
 * Lifecycle of a {@link PaymentCredit} (saldo a favor, R12 — Fase 2).
 *
 * <p>{@code AVAILABLE} is a credit not yet applied to any adeudo; {@code
 * APPLIED} is one consumed by a payment ({@code appliedToPaymentId} points at
 * it); {@code REFUNDED} is the (out-of-scope) cash-return state;
 * {@code CANCELLED} voids a credit before it is used. A credit is of the
 * <b>person</b>, so it survives independently of the payment that originated
 * it.
 */
public enum CreditStatus {
	AVAILABLE,
	APPLIED,
	REFUNDED,
	CANCELLED
}
