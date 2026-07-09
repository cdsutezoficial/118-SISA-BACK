package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code AcademicPlan} catalog entries
 * (spec: "List Academic Plans (Paginated)"), mirroring the
 * {@code GET /programs} convention. When {@code status} is omitted, plans of
 * ALL statuses are returned — no default active-only filter (spec: "there is
 * no default active-only filter"). Role authorization is enforced by
 * {@code SecurityFilterConfig}, not here.
 */
public interface ListAcademicPlansUseCase {

	ListAcademicPlansResult listPlans(ListAcademicPlansQuery query);

	/**
	 * @param status    optional — matches the plan's current status; omitted returns all statuses
	 * @param search    optional free-text match
	 * @param programId optional — filters to plans belonging to this program
	 * @param page      zero-based page index; negative values are normalized to 0
	 * @param size      page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListAcademicPlansQuery(PlanStatus status, String search, UUID programId, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListAcademicPlansResult(List<PlanSummary> items, long totalElements, int totalPages, int page, int size) {
	}

	/**
	 * Minimum fields needed for a list view (spec: "Each returned item MUST
	 * include at minimum id, programId, version, validityPeriod,
	 * effectiveFrom, totalLevels, and status").
	 */
	record PlanSummary(UUID id, UUID programId, String version, String validityPeriod, LocalDate effectiveFrom,
			int totalLevels, PlanStatus status) {
	}
}
