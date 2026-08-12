package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code POST /program-admission-configs}. {@code status}
 * is absent — every new config defaults to {@code OPEN} (enforced by the
 * {@code ProgramAdmissionConfig} constructor). {@code selectionStatus} is
 * ALSO absent — always defaults to {@code IN_REVIEW}, never accepted from the
 * caller (plan §5).
 */
public record CreateProgramAdmissionConfigRequest(@NotNull UUID programId, @NotNull UUID periodId,
		@NotNull UUID targetGenerationId, boolean isOffered, int maxCandidates, @NotNull Instant opensAt,
		@NotNull Instant closesAt) {
}
