package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code Group} entries, mirroring
 * {@code ListGenerationsUseCase}'s convention — with an extra
 * {@code generationId} filter, since {@code Group} (unlike {@code Generation})
 * has its own FK to a specific generation. Role authorization is enforced by
 * {@code SecurityFilterConfig}, not here.
 */
public interface ListGroupsUseCase {

	ListGroupsResult listGroups(ListGroupsQuery query);

	/**
	 * @param status       optional — matches the group's current status
	 * @param search       optional free-text match against {@code code}
	 * @param programId    optional — filters to groups belonging to this program
	 * @param generationId optional — filters to groups belonging to this generation
	 * @param page         zero-based page index; negative values are normalized to 0
	 * @param size         page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListGroupsQuery(GroupStatus status, String search, UUID programId, UUID generationId, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListGroupsResult(List<GroupSummary> items, long totalElements, int totalPages, int page, int size) {
	}

	record GroupSummary(UUID id, UUID generationId, UUID periodId, UUID planLevelId, UUID programId, String code,
			int maxCapacity, Shift shift, GroupStatus status) {
	}
}
