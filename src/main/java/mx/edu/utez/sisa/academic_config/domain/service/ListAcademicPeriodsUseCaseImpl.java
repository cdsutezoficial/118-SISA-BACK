package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchPage;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;

import java.util.List;

/**
 * Paginated, filterable query for {@code AcademicPeriod} entries, mirroring
 * {@code ListSubjectClassificationsUseCaseImpl}.
 */
public class ListAcademicPeriodsUseCaseImpl implements ListAcademicPeriodsUseCase {

	private final AcademicPeriodRepository periodRepository;

	public ListAcademicPeriodsUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	public ListAcademicPeriodsResult listPeriods(ListAcademicPeriodsQuery query) {
		PeriodSearchCriteria criteria = new PeriodSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		PeriodSearchPage page = periodRepository.search(criteria);

		List<PeriodSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListAcademicPeriodsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private PeriodSummary toSummary(AcademicPeriod period) {
		return new PeriodSummary(period.getId(), period.getName(), period.getYear(), period.getPeriodNumber(),
				period.getType(), period.getStartDate(), period.getEndDate(), period.getEnrollmentStart(),
				period.getEnrollmentEnd(), period.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListSubjectClassificationsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListAcademicPeriodsQuery#DEFAULT_PAGE_SIZE}; oversized requests
	 * are capped at {@link ListAcademicPeriodsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListAcademicPeriodsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListAcademicPeriodsQuery.MAX_PAGE_SIZE);
	}
}
