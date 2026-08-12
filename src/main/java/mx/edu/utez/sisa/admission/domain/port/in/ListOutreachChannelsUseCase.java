package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code OutreachChannel} catalog entries,
 * mirroring {@code ListSubjectClassificationsUseCase}'s convention. Role
 * authorization is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListOutreachChannelsUseCase {

	ListOutreachChannelsResult listChannels(ListOutreachChannelsQuery query);

	/**
	 * @param status optional — matches the channel's current status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListOutreachChannelsQuery(OutreachChannelStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListOutreachChannelsResult(List<OutreachChannelSummary> items, long totalElements, int totalPages,
			int page, int size) {
	}

	record OutreachChannelSummary(UUID id, String name, OutreachChannelStatus status) {
	}
}
