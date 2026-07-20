package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body shared by {@code POST /plans}, {@code PUT /plans/{id}},
 * {@code GET /plans/{id}} and {@code PATCH /plans/{id}/status} — all four
 * return the full post-operation plan state (mirrors
 * {@code CreateAcademicPlanUseCase.AcademicPlanResult}). {@code levels} and
 * {@code gradeScales} are empty except when returned by
 * {@code GET /plans/{id}} (design.md — "GET /plans/{id} is the only endpoint
 * returning the full nested tree").
 */
public record AcademicPlanResponse(UUID id, UUID programId, String version, String validityPeriod,
		String titulationKey, LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade,
		int maxExtraordinaryExamsPerPeriod, boolean requiresSocialService, UUID socialServiceMinLevelId,
		PlanStatus status, List<PlanLevelResponse> levels, List<GradeScaleResponse> gradeScales) {
}
