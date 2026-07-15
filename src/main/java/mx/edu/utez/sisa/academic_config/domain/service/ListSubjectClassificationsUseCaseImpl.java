package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository.ClassificationSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code SubjectClassification} catalog
 * entries, mirroring {@code ListAcademicDivisionsUseCaseImpl}.
 */
public class ListSubjectClassificationsUseCaseImpl implements ListSubjectClassificationsUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public ListSubjectClassificationsUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	public ListSubjectClassificationsResult listClassifications(ListSubjectClassificationsQuery query) {
		ClassificationSearchCriteria criteria = new ClassificationSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		ClassificationSearchPage page = classificationRepository.search(criteria);

		List<ClassificationSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListSubjectClassificationsResult(summaries, page.totalElements(), page.totalPages(),
				criteria.page(), criteria.size());
	}

	private ClassificationSummary toSummary(SubjectClassification classification) {
		return new ClassificationSummary(classification.getId(), classification.getName(), classification.getCode(),
				classification.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListAcademicDivisionsUseCaseImpl}'s convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListSubjectClassificationsQuery#DEFAULT_PAGE_SIZE}; oversized
	 * requests are capped at {@link ListSubjectClassificationsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListSubjectClassificationsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListSubjectClassificationsQuery.MAX_PAGE_SIZE);
	}
}
