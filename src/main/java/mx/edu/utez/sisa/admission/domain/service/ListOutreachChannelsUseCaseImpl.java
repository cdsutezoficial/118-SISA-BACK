package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.ListOutreachChannelsUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code OutreachChannel} catalog entries,
 * mirroring {@code ListSubjectClassificationsUseCaseImpl}.
 */
public class ListOutreachChannelsUseCaseImpl implements ListOutreachChannelsUseCase {

	private final OutreachChannelRepository channelRepository;

	public ListOutreachChannelsUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	public ListOutreachChannelsResult listChannels(ListOutreachChannelsQuery query) {
		OutreachChannelSearchCriteria criteria = new OutreachChannelSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		OutreachChannelSearchPage page = channelRepository.search(criteria);

		List<OutreachChannelSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListOutreachChannelsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private OutreachChannelSummary toSummary(OutreachChannel channel) {
		return new OutreachChannelSummary(channel.getId(), channel.getName(), channel.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListSubjectClassificationsUseCaseImpl}'s
	 * convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListOutreachChannelsQuery#DEFAULT_PAGE_SIZE}; oversized requests
	 * are capped at {@link ListOutreachChannelsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListOutreachChannelsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListOutreachChannelsQuery.MAX_PAGE_SIZE);
	}
}
