package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;

/**
 * Request body for {@code POST /plans/{id}/levels} (design.md — REST
 * endpoints). {@code levelNumber} range/uniqueness validation is enforced by
 * {@code AddPlanLevelUseCaseImpl}/{@code AcademicPlan.addLevel}, not bean
 * validation here.
 */
public record AddPlanLevelRequest(int levelNumber, @NotNull PlanLevelType type, String description) {
}
