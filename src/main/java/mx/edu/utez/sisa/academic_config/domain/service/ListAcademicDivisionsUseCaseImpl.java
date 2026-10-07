package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchPage;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code AcademicDivision} catalog entries
 * (spec: "List Academic Divisions (Paginated)"). Each item carries its real
 * {@code programCount} — a grouped count over {@code AcademicProgram} (one
 * aggregate query per page), replacing the hardcoded stub kept while no
 * {@code AcademicProgram} aggregate existed (HU-PROG-010).
 */
public class ListAcademicDivisionsUseCaseImpl implements ListAcademicDivisionsUseCase {

	private final AcademicDivisionRepository divisionRepository;

	private final AcademicProgramRepository programRepository;

	public ListAcademicDivisionsUseCaseImpl(AcademicDivisionRepository divisionRepository,
			AcademicProgramRepository programRepository) {
		this.divisionRepository = divisionRepository;
		this.programRepository = programRepository;
	}

	@Override
	public ListAcademicDivisionsResult listDivisions(ListAcademicDivisionsQuery query) {
		DivisionSearchCriteria criteria = new DivisionSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		DivisionSearchPage page = divisionRepository.search(criteria);

		Map<UUID, Long> programCounts = programRepository
				.countProgramsByDivisionIds(page.content().stream().map(AcademicDivision::getId).toList());

		List<DivisionSummary> summaries = page.content().stream().map(division -> toSummary(division, programCounts))
				.toList();

		return new ListAcademicDivisionsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private DivisionSummary toSummary(AcademicDivision division, Map<UUID, Long> programCounts) {
		return new DivisionSummary(division.getId(), division.getName(), division.getCode(),
				division.getDescription(), division.getDirectorPersonId(), division.getStatus(),
				programCounts.getOrDefault(division.getId(), 0L).intValue());
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
