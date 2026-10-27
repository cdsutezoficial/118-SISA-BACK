package mx.edu.utez.sisa.finance.domain.model;

/**
 * Which flow generated a {@link Payment} (er-08-pagos.md {@code payment.origin}).
 *
 * <p>{@code ADMISSION} is the ficha; {@code INSCRIPTION} the inscripción
 * flow; {@code CASHIER} an adeudo raised at the ventanilla; {@code LINK} one
 * backed by a {@code payment_link} (Fase 4); {@code MANUAL} a staff-created
 * adeudo with no particular flow behind it. Purely informational in this phase
 * — the settlement logic never branches on it.
 */
public enum PaymentOrigin {
	ADMISSION,
	INSCRIPTION,
	CASHIER,
	LINK,
	MANUAL
}
