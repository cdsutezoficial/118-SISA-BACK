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
 *
 * <p>"Does this concept apply to this program" is answered by the RATES, not by
 * a list stored on the concept. The {@code EXISTS} below is the same
 * three-rung ladder {@link PaymentRateLookupJpaRepository} uses to price:
 * a rate bound to the exact program, or to the program's level, or to neither.
 * A concept with no applicable rate does not apply, and the concept does not
 * repeat that ladder in a second place — the rates are the only statement of
 * scope there is, which is what makes "the ficha is what it costs for this
 * career" true by construction instead of by two lists agreeing.
 *
 * <p>Dropping the old {@code payment_concept_program} join table is also what
 * retires the drift that made the flag removal risky: "applies to" used to be
 * declared once on the concept and priced by a separate lookup, so the two could
 * disagree and the disagreement was invisible until an applicant met it.
 *
 * <p>No {@code is_tuition} predicate, and its absence is the point.
 * {@code isTuition} means "cuota cuatrimestral" — the kind of charge a student
 * pays per term, and what becas and prórrogas read. It said nothing about
 * whether a concept IS the admission ticket, and a catalog that had to light
 * that flag to sell an admission ticket meant the flag was doing two jobs. The
 * ficha is identified by {@code type = ADMISSION} plus an applicable rate;
 * nothing else.
 *
 * <p>The practical consequence: two ACTIVE ADMISSION concepts applicable to the
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
			  AND EXISTS (
			      SELECT r FROM PaymentRate r
			      WHERE r.conceptId = c.id
			        AND r.periodId IS NULL
			        AND r.validFrom <= :onDate
			        AND (r.validTo IS NULL OR r.validTo >= :onDate)
			        AND (r.programId = :programId
			             OR (r.programId IS NULL AND r.level = (
			                  SELECT p.level FROM AcademicProgram p WHERE p.id = :programId))
			             OR (r.programId IS NULL AND r.level IS NULL))
			      )
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
			  AND EXISTS (
			      SELECT r FROM PaymentRate r
			      WHERE r.conceptId = c.id
			        AND r.periodId IS NULL
			        AND (r.programId = :programId
			             OR (r.programId IS NULL AND r.level = (
			                  SELECT p.level FROM AcademicProgram p WHERE p.id = :programId))
			             OR (r.programId IS NULL AND r.level IS NULL))
			      )
			""")
	List<PaymentConcept> findActiveTuitionForProgramIgnoringWindow(@Param("status") PaymentConceptStatus status,
			@Param("type") PaymentConceptType type, @Param("programId") UUID programId);
}