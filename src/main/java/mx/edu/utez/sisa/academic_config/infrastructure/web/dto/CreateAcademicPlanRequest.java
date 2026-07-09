package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /plans} (design.md — REST endpoints).
 * {@code programId} is required (spec: "programId MUST be required").
 * {@code socialServiceMinLevelId} MUST be {@code null} at creation
 * regardless of {@code requiresSocialService} (enforced by
 * {@code CreateAcademicPlanUseCaseImpl}, not bean validation, since it
 * depends on the other field's value). {@code maxExtraordinaryExamsPerPeriod}
 * MUST be {@code >= 0} (spec: "an integer >= 0") — enforced here via
 * {@code @Min}, unlike {@code minPassingGrade}'s range, since this is a
 * simple non-relational floor with no other field dependency, so bean
 * validation (already relied upon for {@code @NotBlank}/{@code @NotNull} on
 * this same record) is sufficient and guarantees a 400 through the existing
 * {@code MethodArgumentNotValidException} handler.
 */
public record CreateAcademicPlanRequest(@NotNull UUID programId, @NotBlank String version,
		@NotBlank String validityPeriod, @NotBlank String titulationKey, @NotNull LocalDate effectiveFrom,
		int totalLevels, @NotNull BigDecimal minPassingGrade, @Min(0) int maxExtraordinaryExamsPerPeriod,
		boolean requiresSocialService, UUID socialServiceMinLevelId) {
}
