package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code PUT /generations/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /generations/{id}/status}. {@code code} is also absent —
 * recomputed server-side, same rationale as {@code CreateGenerationRequest}.
 *
 * <p>
 * {@code @Min(1)} mirrors {@code CreateGenerationRequest}: a primitive {@code int}
 * defaults to 0 when the key is absent, and 0 is not a valid generation number.
 */
public record UpdateGenerationRequest(@NotNull UUID planId, @NotNull UUID startPeriodId, @Min(value = 1, message = "El número de generación debe ser mayor o igual a 1.") int number) {
}