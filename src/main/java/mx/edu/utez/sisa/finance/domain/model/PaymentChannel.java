package mx.edu.utez.sisa.finance.domain.model;

/**
 * Surface an abono ({@link PaymentEntry}) was collected through
 * (er-08-pagos.md {@code payment_entry.channel}).
 *
 * <p>{@code PORTAL} is the person paying online themselves; {@code CASHIER}
 * the ventanilla; {@code LINK} a public payment link (Fase 4). Kept separate
 * from {@link PaymentEntryMethod}: "cash at the window" and "card at the
 * window" share the channel and differ in method.
 */
public enum PaymentChannel {
	PORTAL,
	CASHIER,
	LINK
}
