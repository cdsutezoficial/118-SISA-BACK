package mx.edu.utez.sisa.finance.domain.model;

/**
 * Status of the online charge behind an abono ({@link PaymentEntry}
 * {@code gatewayStatus}), when one exists (Evo Payments). {@code null} for
 * cash abonos — there is no gateway to report on.
 *
 * <p>Carried as a column since this phase (er-08-pagos.md) so the ficha flow's
 * online charges can land without a schema change; the gateway integration
 * itself (checkout/confirm) belongs to the admission flow and to Fase 4/5.
 */
public enum PaymentGatewayStatus {
	PENDING,
	APPROVED,
	DECLINED,
	REFUNDED
}
