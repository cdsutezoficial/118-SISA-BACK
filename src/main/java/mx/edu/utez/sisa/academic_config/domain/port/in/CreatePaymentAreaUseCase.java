package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

import java.util.UUID;

/**
 * Creates a {@code PaymentArea} catalog entry. Companion catalog to
 * {@code PaymentConcept} (frontend "Áreas de facturación"). Role
 * authorization (ADMIN or PERSONAL_FINANZAS — NOT SERVICIOS_ESCOLARES, same
 * as {@code PaymentConcept}) is enforced by {@code SecurityFilterConfig}, not
 * here.
 */
public interface CreatePaymentAreaUseCase {

	PaymentAreaResult createPaymentArea(CreatePaymentAreaCommand command);

	/**
	 * @param name unique (case-insensitive) — verified against
	 *             {@code PaymentAreaRepository#findByName}
	 * @param code unique (case-insensitive) — verified against
	 *             {@code PaymentAreaRepository#findByCode}
	 */
	record CreatePaymentAreaCommand(String name, String code, String description) {
	}

	record PaymentAreaResult(UUID id, String name, String code, String description, PaymentAreaStatus status) {
	}
}
