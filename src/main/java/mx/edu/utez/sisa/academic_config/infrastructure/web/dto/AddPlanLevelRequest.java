package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;

/**
 * Request body for {@code POST /plans/{id}/levels} (design.md — REST
 * endpoints). {@code levelNumber} must be {@code >= 1} and unique within the
 * plan; that is enforced by {@code AddPlanLevelUseCaseImpl}/
 * {@code AcademicPlan.addLevel}, not bean validation here, because the
 * number is a free label (it may exceed {@code totalLevels}) and uniqueness
 * needs the aggregate's state.
 *
 * <p>{@code description} is optional (the column is nullable), so it carries no
 * {@code @NotBlank}; {@code @Size} still applies to it because Hibernate's
 * default column length is 255 and a longer value would fail as a generic
 * database error instead of a field-level 400. Same rationale, and same
 * control-character rule, as the plan's own text fields.
 */
public record AddPlanLevelRequest(int levelNumber,
		@NotNull(message = "Selecciona el tipo de nivel.") PlanLevelType type,
		@Size(max = 255, message = "La descripción del nivel no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción del nivel contiene caracteres no válidos.") String description) {
}
