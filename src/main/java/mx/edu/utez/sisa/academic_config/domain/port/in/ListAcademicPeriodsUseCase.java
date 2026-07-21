package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code AcademicPeriod} entries, mirroring
 * {@code ListSubjectClassificationsUseCase}'s convention. Role authorization
 * is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListAcademicPeriodsUseCase {

	ListAcademicPeriodsResult listPeriods(ListAcademicPeriodsQuery query);

	/**
	 * @param status optional — matches the period's current status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListAcademicPeriodsQuery(PeriodStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListAcademicPeriodsResult(List<PeriodSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record PeriodSummary(UUID id, String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd, PeriodStatus status) {
	}
}
