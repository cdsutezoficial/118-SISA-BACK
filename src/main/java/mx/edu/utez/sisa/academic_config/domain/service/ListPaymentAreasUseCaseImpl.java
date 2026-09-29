package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchPage;

import java.util.List;

/**
 * Paginated, filterable query for {@code PaymentArea} catalog entries,
 * mirroring {@code ListAcademicDivisionsUseCaseImpl}.
 */
public class ListPaymentAreasUseCaseImpl implements ListPaymentAreasUseCase {

	private final PaymentAreaRepository paymentAreaRepository;

	public ListPaymentAreasUseCaseImpl(PaymentAreaRepository paymentAreaRepository) {
		this.paymentAreaRepository = paymentAreaRepository;
	}

	@Override
	public ListPaymentAreasResult listPaymentAreas(ListPaymentAreasQuery query) {
		PaymentAreaSearchCriteria criteria = new PaymentAreaSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));

		PaymentAreaSearchPage page = paymentAreaRepository.search(criteria);

		List<PaymentAreaSummary> summaries = page.content().stream().map(this::toSummary).toList();

		return new ListPaymentAreasResult(summaries, page.totalElements(), page.totalPages(), criteria.page(),
				criteria.size());
	}

	private PaymentAreaSummary toSummary(PaymentArea area) {
		return new PaymentAreaSummary(area.getId(), area.getName(), area.getCode(), area.getDescription(),
				area.getStatus());
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
	 * {@link ListPaymentAreasQuery#DEFAULT_PAGE_SIZE}; oversized requests are
	 * capped at {@link ListPaymentAreasQuery#MAX_PAGE_SIZE}.
	 */
	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListPaymentAreasQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListPaymentAreasQuery.MAX_PAGE_SIZE);
	}
}
