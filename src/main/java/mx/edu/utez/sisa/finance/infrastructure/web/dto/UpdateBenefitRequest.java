package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code PUT /finance/benefits/{id}} body (Fase 2). The subject
 * ({@code personId}/{@code conceptCode}) is immutable and therefore absent.
 */
public record UpdateBenefitRequest(UUID periodId, UUID generationId, @NotNull BenefitMode mode, BigDecimal percent,
		BigDecimal amount, @NotNull LocalDate validFrom, @NotNull LocalDate validTo, @NotBlank String reason) {
}
