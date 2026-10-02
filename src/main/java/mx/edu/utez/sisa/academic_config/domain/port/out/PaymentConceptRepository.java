package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentConcept}, mirroring
 * {@code SubjectClassificationRepository}'s shape. {@code findByCode} exists
 * because {@code code} is a required unique business key;
 * {@code name} stays non-unique.
 */
public interface PaymentConceptRepository {

	PaymentConcept save(PaymentConcept concept);

	/**
	 * Backs {@code GetPaymentConceptUseCase} — same convention as
	 * {@code SubjectClassificationRepository#findById}.
	 */
	Optional<PaymentConcept> findById(UUID id);

	/**
	 * Case-insensitive lookup backing the create/update {@code code} uniqueness
	 * check. {@code excludingId} excludes the concept being edited so an
	 * unchanged re-save is not treated as a collision with itself.
	 */
	Optional<PaymentConcept> findByCode(String code, UUID excludingId);

	/**
	 * Backs the "one ACTIVE recurring quota per level" rule — see
	 * {@code DuplicatePaymentQuotaLevelException} for why two are refused
	 * rather than disambiguated.
	 */
	Optional<PaymentConcept> findActiveByTypeAndLevelNumber(PaymentConceptType type, Integer levelNumber,
			UUID excludingId);

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
