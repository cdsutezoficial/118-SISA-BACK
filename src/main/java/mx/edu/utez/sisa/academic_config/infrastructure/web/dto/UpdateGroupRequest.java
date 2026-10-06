package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Request body for {@code PUT /groups/{id}}. {@code status} is deliberately
 * absent — status transitions go through
 * {@code PATCH /groups/{id}/status}. {@code programId} is also absent —
 * re-resolved server-side, same rationale as {@code CreateGroupRequest}.
 *
 * <p>
 * {@code code} and {@code maxCapacity} carry the exact same constraints as in
 * {@code CreateGroupRequest}; the rationale for each is documented there.
 */
public record UpdateGroupRequest(@NotNull UUID generationId, @NotNull UUID periodId, @NotNull UUID planLevelId,
		@NotNull @Size(max = 10, message = "La clave del grupo no puede exceder 10 caracteres.")
		@Pattern(regexp = "^\\p{N}+\\p{L}$", message = "La clave del grupo debe ser el nivel seguido de una sola letra, por ejemplo 3A.") String code,
		@Min(value = 1, message = "La capacidad máxima debe ser mayor o igual a 1.") int maxCapacity, @NotNull Shift shift) {
}