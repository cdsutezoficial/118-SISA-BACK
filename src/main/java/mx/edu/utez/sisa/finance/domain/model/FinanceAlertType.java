package mx.edu.utez.sisa.finance.domain.model;

/** Why Finanzas has to look at a payment by hand. */
public enum FinanceAlertType {

	/** The bank authorized money and has not captured it for more than three days (E4). */
	AUTHORIZED_NOT_CAPTURED,

	/** A late capture found the career full; the money became saldo a favor (E4). */
	PAID_WITHOUT_QUOTA,

	/** A capture settled nothing (a second capture, or a cancelled charge); kept as saldo a favor (E1). */
	OVERPAYMENT
}
