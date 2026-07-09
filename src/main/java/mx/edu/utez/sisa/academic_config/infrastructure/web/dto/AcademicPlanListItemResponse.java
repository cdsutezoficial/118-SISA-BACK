package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single row of {@code GET /plans} (spec: "List Academic Plans
 * (Paginated)" — "Each returned item MUST include at minimum id, programId,
 * version, validityPeriod, effectiveFrom, totalLevels, and status"), mirrors
 * {@code ListAcademicPlansUseCase.PlanSummary} exactly.
 */
public record AcademicPlanListItemResponse(UUID id, UUID programId, String version, String validityPeriod,
		LocalDate effectiveFrom, int totalLevels, PlanStatus status) {
}
