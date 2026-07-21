package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code PUT /generations/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /generations/{id}/status}. {@code code} is also absent —
 * recomputed server-side, same rationale as {@code CreateGenerationRequest}.
 */
public record UpdateGenerationRequest(@NotNull UUID planId, @NotNull UUID startPeriodId, int number) {
}
