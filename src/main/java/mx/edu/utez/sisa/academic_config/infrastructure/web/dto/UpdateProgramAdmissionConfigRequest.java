package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code PUT /program-admission-configs/{id}}.
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /program-admission-configs/{id}/status}. {@code selectionStatus}
 * is ALSO absent and can never be changed by this endpoint (plan §3/§5).
 */
public record UpdateProgramAdmissionConfigRequest(@NotNull UUID programId, @NotNull UUID periodId,
		@NotNull UUID targetGenerationId, boolean isOffered, int maxCandidates, @NotNull Instant opensAt,
		@NotNull Instant closesAt) {
}
