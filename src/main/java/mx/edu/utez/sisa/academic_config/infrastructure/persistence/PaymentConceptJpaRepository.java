package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentConceptRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows).
 */
public interface PaymentConceptJpaRepository extends JpaRepository<PaymentConcept, UUID> {

	/**
	 * Case-insensitive lookup by {@code code}, backing the create/update
	 * uniqueness check. Same convention as
	 * {@code PaymentAreaJpaRepository#findByCodeIgnoreCase},
	 * {@code AcademicDivisionJpaRepository#findByCodeIgnoreCase} and
	 * {@code SubjectClassificationJpaRepository#findByCodeIgnoreCase}.
	 */
	Optional<PaymentConcept> findByCodeIgnoreCase(String code);

	/**
	 * Backs the "one ACTIVE recurring quota per level" rule. The
	 * {@code excludingId} parameter lets an update exclude the concept being
	 * edited, which is what makes a no-op re-save of an unchanged concept
	 * legal — the same
	 * {@code AcademicPlanJpaRepository#findByProgramIdAndVersionAndIdNot}
	 * shape used for {@code AcademicPlan.version} uniqueness.
	 */
	@Query("""
			SELECT c FROM PaymentConcept c
			WHERE c.type = :type
			  AND c.levelNumber = :levelNumber
			  AND c.status = :status
			  AND (:excludingId IS NULL OR c.id <> :excludingId)
			""")
	Optional<PaymentConcept> findActiveByTypeAndLevelNumber(@Param("type") PaymentConceptType type,
			@Param("levelNumber") Integer levelNumber, @Param("status") PaymentConceptStatus status,
			@Param("excludingId") UUID excludingId);

	/**
	 * Backs {@code ListPaymentConceptsUseCase}, mirroring
	 * {@code SubjectClassificationJpaRepository#search}: both filters are
	 * optional via the {@code (:param IS NULL OR ...)} pattern, with an
	 * explicit {@code countQuery} for consistency with the rest of the
	 * codebase. {@code search} matches {@code name} OR {@code code}, since a
	 * user looking for a concept will try whichever identifier they were given.
	 */
	@Query(value = """
			SELECT c FROM PaymentConcept c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL
			       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(c) FROM PaymentConcept c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL
			       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<PaymentConcept> search(@Param("status") PaymentConceptStatus status, @Param("search") String search,
			Pageable pageable);
}
