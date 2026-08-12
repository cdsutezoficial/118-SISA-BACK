package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /payment-concepts/{conceptId}/rates}.
 * {@code conceptId} itself comes from the path, not this body.
 * {@code programId}/{@code level}/{@code periodId} are all optional (plan
 * section 3). {@code amount > 0} is deliberately NOT bean-validated here —
 * enforced by {@code SetPaymentRateUseCaseImpl} via
 * {@code InvalidPaymentRateDataException}, same convention as
 * {@code CreateAcademicPlanRequest}'s {@code minPassingGrade}. There is no
 * {@code validTo} field — it is server-managed only, never client-supplied
 * (plan section 3).
 */
public record CreatePaymentRateRequest(UUID programId, AcademicLevel level, @NotNull BigDecimal amount,
		UUID periodId, @NotNull LocalDate validFrom) {
}
