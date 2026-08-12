package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentConcept}, mirroring
 * {@code SubjectClassificationRepository}'s shape. No {@code findByCode} /
 * {@code findByName} — this aggregate has no unique business key to look up
 * by (plan section 4).
 */
public interface PaymentConceptRepository {

	PaymentConcept save(PaymentConcept concept);

	/**
	 * Backs {@code GetPaymentConceptUseCase} — same convention as
	 * {@code SubjectClassificationRepository#findById}.
	 */
	Optional<PaymentConcept> findById(UUID id);

	/**
	 * Filterable, paginated query backing {@code ListPaymentConceptsUseCase}.
	 */
	PaymentConceptSearchPage search(PaymentConceptSearchCriteria criteria);

	/**
	 * @param status optional — filters to concepts with this exact status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record PaymentConceptSearchCriteria(PaymentConceptStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link PaymentConcept} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record PaymentConceptSearchPage(List<PaymentConcept> content, long totalElements, int totalPages) {
	}
}
