package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /generations}. {@code code} is deliberately
 * absent — it is always computed server-side from {@code startPeriodId}'s
 * year and {@code number}, even if a client sends one it is ignored.
 * {@code status} is also absent — every new generation defaults to
 * {@code ACTIVE} (enforced by the {@code Generation} constructor).
 * {@code number} is a primitive {@code int} rather than boxed with
 * {@code @NotNull} — same convention as {@code CreateAcademicPeriodRequest.year}.
 */
public record CreateGenerationRequest(@NotNull UUID planId, @NotNull UUID startPeriodId, int number) {
}
