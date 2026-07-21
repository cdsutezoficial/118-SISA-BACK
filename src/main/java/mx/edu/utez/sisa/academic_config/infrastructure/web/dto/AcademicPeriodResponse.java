package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for {@code POST}/{@code PUT}/{@code GET (by id)}/
 * {@code PATCH .../status} on {@code /periods} — mirrors
 * {@code SubjectClassificationResponse}'s "full post-operation state" convention.
 */
public record AcademicPeriodResponse(UUID id, String name, int year, int periodNumber, PeriodType type,
		LocalDate startDate, LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd,
		PeriodStatus status) {
}
