package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A {@code GradeScale} owned by an {@code AcademicPlan}, nested in
 * {@link AcademicPlanResponse#gradeScales()} or returned directly by the
 * {@code POST}/{@code PUT} grade-scale endpoints (design.md — Decision:
 * "Child endpoints return only the child DTO (not the whole plan)") — mirrors
 * {@code PlanLevelResponse}.
 */
public record GradeScaleResponse(UUID id, UUID classificationId, BigDecimal numericMin, BigDecimal numericMax,
		List<GradeScaleEntryResponse> entries) {
}
