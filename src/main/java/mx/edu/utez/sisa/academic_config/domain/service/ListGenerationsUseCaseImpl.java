package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code Generation} entries, mirroring
 * {@code ListAcademicPeriodsUseCaseImpl}.
 */
public class ListGenerationsUseCaseImpl implements ListGenerationsUseCase {

	private final GenerationRepository generationRepository;

	public ListGenerationsUseCaseImpl(GenerationRepository generationRepository) {
		this.generationRepository = generationRepository;
	}

	@Override
	public ListGenerationsResult listGenerations(ListGenerationsQuery query) {
		GenerationSearchCriteria criteria = new GenerationSearchCriteria(query.status(), query.search(),
				query.programId(), normalizePage(query.page()), normalizeSize(query.size()));

		GenerationSearchPage page = generationRepository.search(criteria);

		List<GenerationSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListGenerationsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private GenerationSummary toSummary(Generation generation) {
		return new GenerationSummary(generation.getId(), generation.getPlanId(), generation.getStartPeriodId(),
				generation.getProgramId(), generation.getNumber(), generation.getCode(), generation.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListAcademicPeriodsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListGenerationsQuery#DEFAULT_PAGE_SIZE}; oversized requests are
	 * capped at {@link ListGenerationsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListGenerationsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListGenerationsQuery.MAX_PAGE_SIZE);
	}
}
