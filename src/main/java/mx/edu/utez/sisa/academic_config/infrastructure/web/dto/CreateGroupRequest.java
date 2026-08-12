package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Request body for {@code POST /groups}. {@code programId} is deliberately
 * absent — it is always resolved server-side from {@code generationId}'s
 * owning {@code Generation.programId}, even if a client sends one it is
 * ignored. {@code status} is also absent — every new group defaults to
 * {@code OPEN} (enforced by the {@code Group} constructor).
 * {@code maxCapacity} is a primitive {@code int} rather than boxed with
 * {@code @NotNull} — same convention as {@code CreateGenerationRequest.number}.
 */
public record CreateGroupRequest(@NotNull UUID generationId, @NotNull UUID periodId, @NotNull UUID planLevelId,
		@NotNull String code, int maxCapacity, @NotNull Shift shift) {
}
