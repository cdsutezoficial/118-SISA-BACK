package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for {@code POST}/{@code GET} {@code /payment-concepts/{conceptId}/rates}
 * — includes {@code validTo}, unlike the request body, since it is
 * server-managed (plan section 3).
 */
public record PaymentRateResponse(UUID id, UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount,
		UUID periodId, LocalDate validFrom, LocalDate validTo) {
}
