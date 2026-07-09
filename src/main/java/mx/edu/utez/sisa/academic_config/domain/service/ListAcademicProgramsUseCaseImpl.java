package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code AcademicProgram} catalog entries
 * (spec: "List Academic Programs (Paginated)").
 */
public class ListAcademicProgramsUseCaseImpl implements ListAcademicProgramsUseCase {

	private final AcademicProgramRepository programRepository;

	public ListAcademicProgramsUseCaseImpl(AcademicProgramRepository programRepository) {
		this.programRepository = programRepository;
	}

	@Override
	public ListAcademicProgramsResult listPrograms(ListAcademicProgramsQuery query) {
		ProgramSearchCriteria criteria = new ProgramSearchCriteria(query.divisionId(), query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		ProgramSearchPage page = programRepository.search(criteria);

		List<ProgramSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListAcademicProgramsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private ProgramSummary toSummary(AcademicProgram program) {
		return new ProgramSummary(program.getId(), program.getDivisionId(), program.getName(), program.getOfferName(),
				program.getCode(), program.getLevel(), program.getModality(), program.getDescription(),
				program.getDgpCode(), program.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListAcademicDivisionsUseCaseImpl}'s
	 * convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListAcademicProgramsQuery#DEFAULT_PAGE_SIZE}; oversized requests
	 * are capped at {@link ListAcademicProgramsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListAcademicProgramsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListAcademicProgramsQuery.MAX_PAGE_SIZE);
	}
}
