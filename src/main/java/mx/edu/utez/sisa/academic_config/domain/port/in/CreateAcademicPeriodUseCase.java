package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates an {@code AcademicPeriod} (plan: {@code docs/plans/2026-07-20-academic-period.md}).
 * Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, not here — same convention as
 * {@code CreateSubjectClassificationUseCase}. Always defaults {@code status}
 * to {@link PeriodStatus#CONFIGURATION}.
 */
public interface CreateAcademicPeriodUseCase {

	PeriodResult createPeriod(CreatePeriodCommand command);

	/**
	 * @param year         calendar year
	 * @param periodNumber MUST be unique together with {@code year} across all periods (plan §4)
	 */
	record CreatePeriodCommand(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}

	record PeriodResult(UUID id, String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd, PeriodStatus status) {
	}
}
