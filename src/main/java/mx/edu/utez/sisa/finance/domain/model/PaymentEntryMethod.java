package mx.edu.utez.sisa.finance.domain.model;

/**
 * How an abono ({@link PaymentEntry}) was paid (er-08-pagos.md
 * {@code payment_entry.method}).
 *
 * <p>{@code CASH} is ventanilla (rol {@code CAJA}, Fase 5); {@code ONLINE} /
 * {@code CARD} are the electronic channels (Evo Payments). Transferencia and
 * depósito were deliberately retired (decisión 2026-10-09): only efectivo and
 * en-línea exist. Two Fase 2 values join them, neither of which is collected
 * money: {@code CREDIT} is the saldo-a-favor application and {@code EXEMPTION}
 * is the {@code $0} rastro of a 100 % discount — so the corte de caja (Fase 5)
 * can tell them apart from real collections.
 */
public enum PaymentEntryMethod {
	CASH,
	ONLINE,
	CARD,
	CREDIT,
	EXEMPTION
}
