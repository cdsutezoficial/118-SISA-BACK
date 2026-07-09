package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PUT /plans/{id}} (design.md — REST endpoints).
 * {@code programId} is deliberately absent — moving a plan to a different
 * program is not supported (spec: "programId MUST NOT be changeable via
 * update"). {@code status} is likewise absent — status transitions go
 * through {@code PATCH /plans/{id}/status}. {@code maxExtraordinaryExamsPerPeriod}
 * MUST be {@code >= 0} on update too (spec: "Update Academic Plan" lists it
 * among the updatable fields with no exemption from its floor) — see
 * {@link CreateAcademicPlanRequest} for why {@code @Min} is used here.
 */
public record UpdateAcademicPlanRequest(@NotBlank String version, @NotBlank String validityPeriod,
		@NotBlank String titulationKey, @NotNull LocalDate effectiveFrom, int totalLevels,
		@NotNull BigDecimal minPassingGrade, @Min(0) int maxExtraordinaryExamsPerPeriod, boolean requiresSocialService,
		UUID socialServiceMinLevelId) {
}
