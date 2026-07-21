package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single row of {@code GET /periods}.
 */
public record AcademicPeriodListItemResponse(UUID id, String name, int year, int periodNumber, PeriodType type,
		LocalDate startDate, LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd,
		PeriodStatus status) {
}
