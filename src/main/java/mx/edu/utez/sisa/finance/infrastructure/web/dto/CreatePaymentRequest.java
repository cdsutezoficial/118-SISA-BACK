package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /finance/payments} body. {@code onDate} is optional — the
 * server defaults it to today (Mexico-zone clock); it exists so staff can
 * quote "as of" a date without a separate endpoint. Exactly one of
 * {@code candidateId}/{@code studentId} anchors the adeudo.
 */
public record CreatePaymentRequest(UUID candidateId, UUID studentId, @NotBlank String conceptCode,
		UUID periodId, UUID generationId, @NotNull UUID programId, AcademicLevel level, @NotNull PaymentOrigin origin,
		Instant dueAt, LocalDate onDate) {

	@AssertTrue(message = "Debes indicar exactamente un ancla: candidateId o studentId")
	public boolean isAnchorValid() {
		return (candidateId != null) ^ (studentId != null);
	}
}
