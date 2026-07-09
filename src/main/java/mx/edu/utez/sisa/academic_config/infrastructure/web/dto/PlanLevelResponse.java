package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;

import java.util.List;
import java.util.UUID;

/**
 * A {@code PlanLevel} owned by an {@code AcademicPlan}, nested in
 * {@link AcademicPlanResponse#levels()} or returned directly by the
 * {@code POST}/{@code PUT} level endpoints (design.md — Decision: "Child
 * endpoints return only the child DTO (not the whole plan)").
 */
public record PlanLevelResponse(UUID id, int levelNumber, PlanLevelType type, String description,
		List<SubjectResponse> subjects) {
}
