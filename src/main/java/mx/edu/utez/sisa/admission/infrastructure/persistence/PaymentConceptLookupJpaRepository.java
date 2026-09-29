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
 * <p>No {@code is_tuition} predicate, and its absence is the point.
 * {@code isTuition} means "cuota cuatrimestral" — the kind of charge a student
 * pays per term, and what becas and prórrogas read. It said nothing about
 * whether a concept IS the admission ticket, and a catalog that had to light
 * that flag to sell an admission ticket meant the flag was doing two jobs. The
 * ficha is identified by {@code type = ADMISSION} plus the program plus the
 * window; nothing else.
 *
 * <p>The practical consequence: two ACTIVE ADMISSION concepts applying to the
 * same program used to be narrowed to one by the flag, and now both survive the
 * query, so {@code FichaAmountResolver}'s "exactly one" rule raises
 * {@code 409 ADMISSION_CONCEPT_AMBIGUOUS} instead of silently picking a fee. That
 * is the correct answer — an admission ticket that could be two different
 * concepts has no defensible price — but it is a behaviour change for a catalog
 * that was carrying a second tuition-flagged admission concept, and such a
 * catalog needs one of the two retired before this ships.
 */
public interface PaymentConceptLookupJpaRepository extends JpaRepository<PaymentConcept, UUID> {

	@Query(value = """
			SELECT c FROM PaymentConcept c
			WHERE c.status = :status
			  AND c.type = :type
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
			  AND :programId MEMBER OF c.programIds
			""")
	List<PaymentConcept> findActiveTuitionForProgramIgnoringWindow(@Param("status") PaymentConceptStatus status,
			@Param("type") PaymentConceptType type, @Param("programId") UUID programId);
}