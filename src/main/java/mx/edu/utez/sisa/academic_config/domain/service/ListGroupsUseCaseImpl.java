package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code Group} entries, mirroring
 * {@code ListGenerationsUseCaseImpl}.
 */
public class ListGroupsUseCaseImpl implements ListGroupsUseCase {

	private final GroupRepository groupRepository;

	public ListGroupsUseCaseImpl(GroupRepository groupRepository) {
		this.groupRepository = groupRepository;
	}

	@Override
	public ListGroupsResult listGroups(ListGroupsQuery query) {
		GroupSearchCriteria criteria = new GroupSearchCriteria(query.status(), query.search(), query.programId(),
				query.generationId(), normalizePage(query.page()), normalizeSize(query.size()));

		GroupSearchPage page = groupRepository.search(criteria);

		List<GroupSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListGroupsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private GroupSummary toSummary(Group group) {
		return new GroupSummary(group.getId(), group.getGenerationId(), group.getPeriodId(), group.getPlanLevelId(),
				group.getProgramId(), group.getCode(), group.getMaxCapacity(), group.getShift(), group.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListGenerationsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to {@link ListGroupsQuery#DEFAULT_PAGE_SIZE};
	 * oversized requests are capped at {@link ListGroupsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListGroupsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListGroupsQuery.MAX_PAGE_SIZE);
	}
}
