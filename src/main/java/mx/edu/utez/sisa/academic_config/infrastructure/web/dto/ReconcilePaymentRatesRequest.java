package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PUT /payment-concepts/{conceptId}/rates} — the
 * concept's COMPLETE desired rate set, replacing the previous single-rate
 * {@code POST}. {@code conceptId} comes from the path, not this body.
 *
 * <p>
 * Nothing here is bean-validated: an amount of zero and a destination priced
 * twice are both decisions the reconciliation reports against the specific row
 * it rejected, which a field-path 400 cannot do.
 */
public record ReconcilePaymentRatesRequest(@Valid List<PaymentRateDraftRequest> rates) {

	/**
	 * @param amount no {@code @NotNull} — a null amount names the destination
	 *               that was left unpriced, which is a different and more useful
	 *               message than "field required" on an anonymous row
	 */
	public record PaymentRateDraftRequest(UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId) {
	}
}