package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;

/**
 * Request body for {@code PUT /plans/{id}/levels/{levelId}} (design.md —
 * REST endpoints).
 */
public record UpdatePlanLevelRequest(int levelNumber, @NotNull PlanLevelType type, String description) {
}
