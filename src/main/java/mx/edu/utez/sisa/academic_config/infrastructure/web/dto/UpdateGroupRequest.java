package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Request body for {@code PUT /groups/{id}}. {@code status} is deliberately
 * absent — status transitions go through {@code PATCH /groups/{id}/status}.
 * {@code programId} is also absent — re-resolved server-side, same rationale
 * as {@code CreateGroupRequest}.
 */
public record UpdateGroupRequest(@NotNull UUID generationId, @NotNull UUID periodId, @NotNull UUID planLevelId,
		@NotNull String code, int maxCapacity, @NotNull Shift shift) {
}
