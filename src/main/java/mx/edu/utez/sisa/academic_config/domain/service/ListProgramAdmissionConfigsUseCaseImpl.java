package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code ProgramAdmissionConfig} entries,
 * mirroring {@code ListGenerationsUseCaseImpl}.
 */
public class ListProgramAdmissionConfigsUseCaseImpl implements ListProgramAdmissionConfigsUseCase {

	private final ProgramAdmissionConfigRepository configRepository;

	public ListProgramAdmissionConfigsUseCaseImpl(ProgramAdmissionConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	@Override
	public ListProgramAdmissionConfigsResult listProgramAdmissionConfigs(ListProgramAdmissionConfigsQuery query) {
		ProgramAdmissionConfigSearchCriteria criteria = new ProgramAdmissionConfigSearchCriteria(query.status(),
				query.programId(), normalizePage(query.page()), normalizeSize(query.size()));

		ProgramAdmissionConfigSearchPage page = configRepository.search(criteria);

		List<ProgramAdmissionConfigSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListProgramAdmissionConfigsResult(summaries, page.totalElements(), page.totalPages(),
				criteria.page(), criteria.size());
	}

	private ProgramAdmissionConfigSummary toSummary(ProgramAdmissionConfig config) {
		return new ProgramAdmissionConfigSummary(config.getId(), config.getProgramId(), config.getPeriodId(),
				config.getTargetGenerationId(), config.isOffered(), config.getMaxCandidates(), config.getOpensAt(),
				config.getClosesAt(), config.getStatus(), config.getSelectionStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListGenerationsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListProgramAdmissionConfigsQuery#DEFAULT_PAGE_SIZE}; oversized
	 * requests are capped at {@link ListProgramAdmissionConfigsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListProgramAdmissionConfigsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListProgramAdmissionConfigsQuery.MAX_PAGE_SIZE);
	}
}
