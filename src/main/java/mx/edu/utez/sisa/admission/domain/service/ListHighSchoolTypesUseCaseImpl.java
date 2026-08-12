package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code HighSchoolType} catalog entries,
 * mirroring {@code ListOutreachChannelsUseCaseImpl}.
 */
public class ListHighSchoolTypesUseCaseImpl implements ListHighSchoolTypesUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public ListHighSchoolTypesUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	public ListHighSchoolTypesResult listHighSchoolTypes(ListHighSchoolTypesQuery query) {
		HighSchoolTypeSearchCriteria criteria = new HighSchoolTypeSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		HighSchoolTypeSearchPage page = highSchoolTypeRepository.search(criteria);

		List<HighSchoolTypeSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListHighSchoolTypesResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private HighSchoolTypeSummary toSummary(HighSchoolType highSchoolType) {
		return new HighSchoolTypeSummary(highSchoolType.getId(), highSchoolType.getName(), highSchoolType.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListOutreachChannelsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListHighSchoolTypesQuery#DEFAULT_PAGE_SIZE}; oversized requests
	 * are capped at {@link ListHighSchoolTypesQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListHighSchoolTypesQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListHighSchoolTypesQuery.MAX_PAGE_SIZE);
	}
}
