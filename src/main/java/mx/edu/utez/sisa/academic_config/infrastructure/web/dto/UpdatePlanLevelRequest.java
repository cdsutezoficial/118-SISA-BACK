package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;

/**
 * Request body for {@code PUT /plans/{id}/levels/{levelId}} (design.md —
 * REST endpoints).
 *
 * <p>Shares every constraint and message with {@link AddPlanLevelRequest}: the
 * two records must stay in sync, since a field the create accepts but the
 * update rejects is a confusing asymmetry.
 */
public record UpdatePlanLevelRequest(int levelNumber,
		@NotNull(message = "Selecciona el tipo de nivel.") PlanLevelType type,
		@Size(max = 255, message = "La descripción del nivel no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción del nivel contiene caracteres no válidos.") String description) {
}
