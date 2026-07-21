package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;

/**
 * Request body for {@code PUT /periods/{id}}. {@code status} is deliberately
 * absent — status transitions go through {@code PATCH /periods/{id}/status}.
 */
public record UpdateAcademicPeriodRequest(@NotBlank String name, int year, int periodNumber,
		@NotNull PeriodType type, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
		@NotNull LocalDate enrollmentStart, @NotNull LocalDate enrollmentEnd) {
}
