package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;

/**
 * Builds the EVO {@code order.id} from the environment identifier
 * ({@code EVO_ORDER_ID_PREFIX}, e.g. {@code TESTUTEZ}) plus the candidate's
 * ficha folio — the "append the identifier + the payer's id/folio to the
 * order id" rule in the integration notes.
 *
 * <p>The gateway constrains {@code order.id} to {@code [0-9A-Za-z_-]} and ≤ 40
 * chars; this project additionally caps it at {@code EVO_ORDER_ID_LENGTH}
 * (32). Example: {@code TESTUTEZ-ADM-2026-000001} (24 chars, OK).
 */
public class OrderIdBuilder {

	private final String prefix;

	private final int maxLength;

	public OrderIdBuilder(String prefix, int maxLength) {
		this.prefix = prefix == null ? "" : prefix.trim();
		this.maxLength = maxLength <= 0 ? 32 : maxLength;
	}

	public String build(String folio) {
		if (folio == null || folio.isBlank()) {
			throw new EvoPaymentGatewayException("No se pudo generar el identificador del pedido: folio vacío.");
		}
		String base = prefix.isEmpty() ? folio.trim() : prefix + "-" + folio.trim();
		String sanitized = base.replaceAll("[^0-9A-Za-z_-]", "").replaceAll("^-+|-+$", "");
		if (sanitized.isEmpty()) {
			throw new EvoPaymentGatewayException("No se pudo generar el identificador del pedido.");
		}
		return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
	}
}