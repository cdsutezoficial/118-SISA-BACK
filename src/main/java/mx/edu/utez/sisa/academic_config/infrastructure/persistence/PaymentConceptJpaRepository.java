package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentConceptRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows).
 */
public interface PaymentConceptJpaRepository extends JpaRepository<PaymentConcept, UUID> {

	/**
	 * Backs {@code ListPaymentConceptsUseCase}, mirroring
	 * {@code SubjectClassificationJpaRepository#search}: both filters are
	 * optional via the {@code (:param IS NULL OR ...)} pattern, with an
	 * explicit {@code countQuery} for consistency with the rest of the
	 * codebase. Unlike {@code SubjectClassification}, {@code search} only
	 * matches {@code name} — this aggregate has no {@code code} field.
	 */
	@Query(value = """
			SELECT c FROM PaymentConcept c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(c) FROM PaymentConcept c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<PaymentConcept> search(@Param("status") PaymentConceptStatus status, @Param("search") String search,
			Pageable pageable);
}
