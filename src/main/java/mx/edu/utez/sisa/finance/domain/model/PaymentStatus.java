package mx.edu.utez.sisa.finance.domain.model;

/**
 * Lifecycle of a {@link Payment} (adeudo / cuenta por cobrar).
 *
 * <p>{@code QUOTED} is a freshly created debt with no abonos; {@code PARTIAL}
 * has abonos that do not yet cover {@code amountDue}; {@code PAID} is settled.
 * {@code EXEMPT} is the deliberate $0 case: a {@code FULL} benefit covers the
 * whole amount, so the payment exists with rastro (a receipt can be tied to the
 * benefit) but nothing is collected — it counts as settled for cupo/solvencia.
 * {@code OVERDUE} is reserved for the mora flow (Fase 3/5) and is not written
 * by this phase's use cases; {@code CANCELLED} voids the adeudo without
 * deleting it.
 */
public enum PaymentStatus {
	QUOTED,
	PARTIAL,
	PAID,
	EXEMPT,
	OVERDUE,
	CANCELLED;

	/**
	 * Whether the debt still accepts abonos. {@code EXEMPT} is deliberately
	 * absent: a fully covered payment is settled the moment it is created, and
	 * letting an abono land on it would invent money the person never owed.
	 */
	public boolean acceptsEntries() {
		return this == QUOTED || this == PARTIAL || this == OVERDUE;
	}
}
