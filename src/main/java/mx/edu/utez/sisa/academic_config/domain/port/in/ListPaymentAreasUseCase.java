package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code PaymentArea} catalog entries. Role
 * authorization is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListPaymentAreasUseCase {

	ListPaymentAreasResult listPaymentAreas(ListPaymentAreasQuery query);

	/**
	 * @param status optional — matches the area's current status
	 * @param search optional free-text match against {@code name} or {@code code}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListPaymentAreasQuery(PaymentAreaStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListPaymentAreasResult(List<PaymentAreaSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record PaymentAreaSummary(UUID id, String name, String code, String description, PaymentAreaStatus status) {
	}
}
