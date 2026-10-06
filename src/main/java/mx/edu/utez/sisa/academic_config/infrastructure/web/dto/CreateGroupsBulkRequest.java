package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Request body for {@code POST /groups/bulk}.
 *
 * <p>
 * There is no {@code code} and no {@code programId}: the codes are allocated by
 * {@code GroupCodeSequence} and the program comes from the generation. The
 * remaining fields are exactly the ones every group in the batch shares, which
 * is the point of the endpoint — a batch where each group had its own shift or
 * capacity would not be a batch.
 *
 * <p>
 * {@code quantity} is capped at 26 because that is the size of the A–Z letter
 * range; see {@code GroupCodeSequence} for why there is no convention past "Z".
 * {@code maxCapacity} has a floor but no ceiling, matching
 * {@code CreateGroupRequest} and the user's decision of 2026-10-05.
 */
public record CreateGroupsBulkRequest(@NotNull UUID generationId, @NotNull UUID periodId, @NotNull UUID planLevelId,
		@Min(value = 1, message = "La cantidad de grupos debe ser mayor o igual a 1.") @Max(value = 26, message = "La cantidad de grupos no puede exceder 26.") int quantity,
		@Min(value = 1, message = "La capacidad máxima debe ser mayor o igual a 1.") int maxCapacity, @NotNull Shift shift) {
}