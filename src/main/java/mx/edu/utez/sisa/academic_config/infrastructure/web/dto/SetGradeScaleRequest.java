package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
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
 *
 * <p>{@code @Digits(integer = 4, fraction = 1)} on both bounds is what the
 * columns allow ({@code precision = 5, scale = 1}). It was missing: a payload
 * with {@code numericMin = 12345.6} passed both bean validation and the
 * {@code min < max} check, then died in the flush as a
 * {@code DataIntegrityViolationException} surfaced as a generic 400 that names
 * no field. The frontend did not check the range either, so the hole was
 * reachable end to end.
 *
 * <p>Real grade scales use values like {@code [0, 100]}, which is why the
 * column is deliberately that wide (see {@code GradeScale}'s javadoc) — the
 * frontend mirrors it with {@code decimal({ intDigits: 4, fraction: 1 })}.
 */
public record SetGradeScaleRequest(
		@NotNull(message = "Selecciona la clasificación.") UUID classificationId,
		@NotNull(message = "La calificación mínima es obligatoria.") @Digits(integer = 4, fraction = 1, message = "La calificación mínima admite un decimal como máximo.") BigDecimal numericMin,
		@NotNull(message = "La calificación máxima es obligatoria.") @Digits(integer = 4, fraction = 1, message = "La calificación máxima admite un decimal como máximo.") BigDecimal numericMax,
		@NotNull(message = "Agrega al menos un rango.") List<@Valid GradeScaleEntryRequest> entries) {
}
