package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;

/**
 * Request body for {@code POST /periods}. {@code status} is deliberately
 * absent — every new period defaults to {@code CONFIGURATION} (enforced by
 * the {@code AcademicPeriod} constructor). {@code year}/{@code periodNumber}
 * are primitive {@code int} rather than boxed with {@code @NotNull} — same
 * convention as {@code CreateAcademicPlanRequest.totalLevels} (a simple
 * required numeric field with no other-field dependency).
 */
public record CreateAcademicPeriodRequest(@NotBlank String name, int year, int periodNumber,
		@NotNull PeriodType type, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
		@NotNull LocalDate enrollmentStart, @NotNull LocalDate enrollmentEnd) {
}
