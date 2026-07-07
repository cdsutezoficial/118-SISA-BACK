package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code AcademicDivision} catalog entries
 * (spec: "List Academic Divisions (Paginated)"). Every returned item carries
 * a hardcoded {@code programCount = 0} stub — no {@code AcademicProgram}
 * aggregate exists yet (HU-PROG-010).
 */
public class ListAcademicDivisionsUseCaseImpl implements ListAcademicDivisionsUseCase {

	private final AcademicDivisionRepository divisionRepository;

	public ListAcademicDivisionsUseCaseImpl(AcademicDivisionRepository divisionRepository) {
		this.divisionRepository = divisionRepository;
	}

	@Override
	public ListAcademicDivisionsResult listDivisions(ListAcademicDivisionsQuery query) {
		DivisionSearchCriteria criteria = new DivisionSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		DivisionSearchPage page = divisionRepository.search(criteria);

		List<DivisionSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListAcademicDivisionsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private DivisionSummary toSummary(AcademicDivision division) {
		return new DivisionSummary(division.getId(), division.getName(), division.getCode(),
				division.getDescription(), division.getDirectorPersonId(), division.getStatus(), 0);
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code identity.ListUsersUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListAcademicDivisionsQuery#DEFAULT_PAGE_SIZE}; oversized
	 * requests are capped at {@link ListAcademicDivisionsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListAcademicDivisionsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListAcademicDivisionsQuery.MAX_PAGE_SIZE);
	}
}
