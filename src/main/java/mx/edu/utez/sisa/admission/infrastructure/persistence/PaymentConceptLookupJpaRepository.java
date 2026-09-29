package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only Spring Data lookup over {@code PaymentConcept} (lives in
 * {@code academic_config}) backing {@link PaymentConceptQueryAdapter} — same
 * cross-context pattern as {@code ProgramAdmissionConfigLookupJpaRepository}.
 * {@code :programId MEMBER OF c.programIds} targets the
 * {@code @ElementCollection} join table {@code payment_concept_program}.
 *
 * <p>{@code c.isTuition = true} narrows the {@code ADMISSION} concepts of a
 * program to the one that IS the admission ticket: a program may carry other
 * active tuition-flagged concepts (campus fees, material) that must never be
 * priced as the ficha. Without this predicate
 * {@code FichaAmountResolver}'s "exactly one active concept" rule would raise
 * {@code 409 AmbiguousFichaPaymentConceptException} as soon as a second such
 * concept existed. Seeded from {@code seed/payment_concepts.csv} column
 * {@code is_tuition}.
 */
public interface PaymentConceptLookupJpaRepository extends JpaRepository<PaymentConcept, UUID> {

	@Query(value = """
			SELECT c FROM PaymentConcept c
			WHERE c.status = :status
			  AND c.type = :type
			  AND c.isTuition = true
			  AND :programId MEMBER OF c.programIds
			  AND (c.availableFrom IS NULL OR c.availableFrom <= :onDate)
			  AND (c.availableUntil IS NULL OR c.availableUntil >= :onDate)
			""")
	List<PaymentConcept> findActiveTuitionForProgram(@Param("status") PaymentConceptStatus status,
			@Param("type") PaymentConceptType type, @Param("programId") UUID programId,
			@Param("onDate") LocalDate onDate);

	/**
	 * Identical to {@link #findActiveTuitionForProgram} minus the two
	 * {@code availableFrom}/{@code availableUntil} predicates.
	 *
	 * <p>Kept as a separate method rather than a nullable {@code onDate} on the
	 * existing one on purpose: {@code (c.availableFrom IS NULL OR c.availableFrom
	 * <= :onDate)} with a null parameter silently yields the empty list, so one
	 * method would have to branch on null to mean two different things, and a
	 * caller that forgot the branch would get a plausible-looking empty result
	 * instead of an error.
	 */
	@Query(value = """
			SELECT c FROM PaymentConcept c
			WHERE c.status = :status
			  AND c.type = :type
			  AND c.isTuition = true
			  AND :programId MEMBER OF c.programIds
			""")
	List<PaymentConcept> findActiveTuitionForProgramIgnoringWindow(@Param("status") PaymentConceptStatus status,
			@Param("type") PaymentConceptType type, @Param("programId") UUID programId);
}