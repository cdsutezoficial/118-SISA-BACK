package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code PaymentConcept} catalog entries,
 * mirroring {@code ListSubjectClassificationsUseCase}'s convention. Role
 * authorization is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListPaymentConceptsUseCase {

	ListPaymentConceptsResult listPaymentConcepts(ListPaymentConceptsQuery query);

	/**
	 * @param status optional — matches the concept's current status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListPaymentConceptsQuery(PaymentConceptStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListPaymentConceptsResult(List<PaymentConceptSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record PaymentConceptSummary(UUID id, String name, PaymentConceptType type, boolean isTuition,
			boolean isStandalone, PaymentConceptStatus status) {
	}
}
