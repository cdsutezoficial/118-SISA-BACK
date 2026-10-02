package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for {@code GET}/{@code PUT}
 * {@code /payment-concepts/{conceptId}/rates} — {@code status} and
 * {@code createdAt} are server-managed, which is why they appear here and never
 * on a request.
 *
 * <p>
 * {@code GET} returns every row, not just the current one, so a caller can show
 * what a price used to be and when it changed; that is the whole reason the
 * history is kept instead of updated in place.
 */
public record PaymentRateResponse(UUID id, UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount,
		UUID periodId, PaymentRateStatus status, LocalDateTime createdAt) {
}