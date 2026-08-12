package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code HighSchoolType} catalog entries,
 * mirroring {@code ListOutreachChannelsUseCase}'s convention.
 */
public interface ListHighSchoolTypesUseCase {

	ListHighSchoolTypesResult listHighSchoolTypes(ListHighSchoolTypesQuery query);

	/**
	 * @param status optional — matches the type's current status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListHighSchoolTypesQuery(HighSchoolTypeStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListHighSchoolTypesResult(List<HighSchoolTypeSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record HighSchoolTypeSummary(UUID id, String name, HighSchoolTypeStatus status) {
	}
}
