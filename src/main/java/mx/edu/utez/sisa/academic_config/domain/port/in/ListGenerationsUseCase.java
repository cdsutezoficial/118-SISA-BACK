package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code Generation} entries, mirroring
 * {@code ListAcademicPeriodsUseCase}'s convention. Role authorization is
 * enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListGenerationsUseCase {

	ListGenerationsResult listGenerations(ListGenerationsQuery query);

	/**
	 * @param status    optional — matches the generation's current status
	 * @param search    optional free-text match against {@code code}
	 * @param programId optional — filters to generations belonging to this program (plan: "useful
	 *                  since number is scoped per program, likely a common filter")
	 * @param page      zero-based page index; negative values are normalized to 0
	 * @param size      page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListGenerationsQuery(GenerationStatus status, String search, UUID programId, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListGenerationsResult(List<GenerationSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record GenerationSummary(UUID id, UUID planId, UUID startPeriodId, UUID programId, int number, String code,
			GenerationStatus status) {
	}
}
