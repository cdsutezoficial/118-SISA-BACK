package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body shared by {@code POST /plans/{id}/grade-scales} (create) and
 * {@code PUT /plans/{id}/grade-scales/{scaleId}} (full replace) — a single
 * "set complete" operation submits the scale and all of its entries together
 * (docs/plans/2026-07-20-grade-scale.md §1). {@code classificationId}
 * existence and the {@code numericMin < numericMax} range are enforced by
 * {@code SetGradeScaleUseCaseImpl}/{@code UpdateGradeScaleUseCaseImpl}, not
 * bean validation here.
 */
public record SetGradeScaleRequest(@NotNull UUID classificationId, @NotNull BigDecimal numericMin,
		@NotNull BigDecimal numericMax, @NotNull List<@Valid GradeScaleEntryRequest> entries) {
}
