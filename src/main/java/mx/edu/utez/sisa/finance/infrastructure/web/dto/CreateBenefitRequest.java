package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /finance/benefits} body (Fase 2). {@code percent} /
 * {@code amount} are mode-specific; {@code grantedByName} is the display name
 * of the acting user (the JWT carries only the id), stored for the audit trail.
 * Exactly one of {@code candidateId}/{@code studentId} anchors the discount.
 */
public record CreateBenefitRequest(UUID candidateId, UUID studentId, String conceptCode, UUID periodId,
		UUID generationId, @NotNull BenefitMode mode, BigDecimal percent, BigDecimal amount,
		@NotNull LocalDate validFrom, @NotNull LocalDate validTo, @NotBlank String reason,
		@NotBlank String grantedByName, UUID paymentId) {

	/**
	 * With a charge ({@code paymentId}) the anchor and concept come from it.
	 * Without one, exactly one anchor and the concept are required.
	 */
	@AssertTrue(message = "Indica el cargo (paymentId) o exactamente un ancla (candidateId o studentId) con su concepto")
	public boolean isAnchorValid() {
		if (paymentId != null) {
			return true;
		}
		return ((candidateId != null) ^ (studentId != null)) && conceptCode != null && !conceptCode.isBlank();
	}
}
