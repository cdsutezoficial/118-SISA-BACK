package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code PaymentConcept} catalog entries,
 * mirroring {@code ListSubjectClassificationsUseCaseImpl}.
 */
public class ListPaymentConceptsUseCaseImpl implements ListPaymentConceptsUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	public ListPaymentConceptsUseCaseImpl(PaymentConceptRepository paymentConceptRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
	}

	@Override
	public ListPaymentConceptsResult listPaymentConcepts(ListPaymentConceptsQuery query) {
		PaymentConceptSearchCriteria criteria = new PaymentConceptSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		PaymentConceptSearchPage page = paymentConceptRepository.search(criteria);

		List<PaymentConceptSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListPaymentConceptsResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private PaymentConceptSummary toSummary(PaymentConcept concept) {
		return new PaymentConceptSummary(concept.getId(), concept.getName(), concept.getType(), concept.isTuition(),
				concept.isStandalone(), concept.getStatus());
	}

	/**
	 * Negative page indexes are normalized to the first page rather than
	 * rejected — matches {@code ListSubjectClassificationsUseCaseImpl}'s
	 * convention.
	 */
	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	/**
	 * Non-positive sizes fall back to
	 * {@link ListPaymentConceptsQuery#DEFAULT_PAGE_SIZE}; oversized requests are
	 * capped at {@link ListPaymentConceptsQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListPaymentConceptsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListPaymentConceptsQuery.MAX_PAGE_SIZE);
	}
}
