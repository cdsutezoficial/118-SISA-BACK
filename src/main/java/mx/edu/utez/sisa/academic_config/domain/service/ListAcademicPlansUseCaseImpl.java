package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code AcademicPlan} catalog entries
 * (spec: "List Academic Plans (Paginated)").
 */
public class ListAcademicPlansUseCaseImpl implements ListAcademicPlansUseCase {

	private final AcademicPlanRepository planRepository;

	public ListAcademicPlansUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	public ListAcademicPlansResult listPlans(ListAcademicPlansQuery query) {
		PlanSearchCriteria criteria = new PlanSearchCriteria(query.programId(), query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		PlanSearchPage page = planRepository.search(criteria);

		List<PlanSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListAcademicPlansResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private PlanSummary toSummary(AcademicPlan plan) {
		return new PlanSummary(plan.getId(), plan.getProgramId(), plan.getVersion(), plan.getValidityPeriod(),
				plan.getEffectiveFrom(), plan.getTotalLevels(), plan.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListAcademicProgramsUseCaseImpl}'s
	 * convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListAcademicPlansQuery#DEFAULT_PAGE_SIZE}; oversized requests
	 * are capped at {@link ListAcademicPlansQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListAcademicPlansQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListAcademicPlansQuery.MAX_PAGE_SIZE);
	}
}
