package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /generations}. {@code code} is deliberately
 * absent — it is always computed server-side from {@code startPeriodId}'s
 * year and {@code number}, even if a client sends one it is ignored.
 * {@code status} is also absent — every new generation defaults to
 * {@code ACTIVE} (enforced by the {@code Generation} constructor).
 *
 * <p>
 * There is no text field here at all, so this record has nothing to normalize:
 * {@code code} is computed and {@code number} is a counter. Hence no
 * {@code GenerationTextNormalizer}, unlike {@code CreateSubjectClassificationRequest}.
 *
 * <p>
 * {@code number} is a primitive {@code int} rather than boxed with
 * {@code @NotNull} — same convention as {@code CreateAcademicPeriodRequest.year}.
 * A primitive defaults to 0 when the key is absent, so {@code @Min(1)} is what
 * rejects a missing field, not {@code @NotNull}. It has no upper bound on
 * purpose: {@code number} is a per-program sequential counter that never resets
 * (see {@code Generation}'s class javadoc), so any ceiling would be arbitrary.
 */
public record CreateGenerationRequest(@NotNull UUID planId, @NotNull UUID startPeriodId, @Min(value = 1, message = "El número de generación debe ser mayor o igual a 1.") int number) {
}