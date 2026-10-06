package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read-only Spring Data lookup over {@code PaymentRate} (lives in
 * {@code academic_config}) backing the pricing half of
 * {@code PaymentConceptQueryAdapter} — same cross-context pattern as
 * {@link PaymentConceptLookupJpaRepository}.
 *
 * <p>Unlike {@code PaymentRateJpaRepository.findActive}, which matches ONE exact
 * {@code (programId, level, periodId)} combination and is what
 * {@code ReconcilePaymentRatesUseCase} uses to find the row an incoming amount
 * supersedes, this returns EVERY rate of a concept that could price a given
 * program, so the caller can pick between rungs of different specificity.
 *
 * <p>The three rungs, all restricted to continuous rates
 * ({@code periodId == null} — a period-scoped row prices a period, not an
 * admission ticket) and to rows currently in force:
 * <ol>
 * <li>the row bound to that exact {@code programId};
 * <li>the row bound to the program's {@code academic_program.level};
 * <li>the row bound to neither.
 * </ol>
 *
 * <p>"In force" is {@code status = ACTIVE}, which replaced a
 * {@code validFrom <= onDate <= validTo} range test. The price's validity
 * window lives on the concept ({@code availableFrom}/{@code availableUntil}),
 * which is filtered by the concept-level query; asking each rate row to restate
 * a date range meant a price correction had to invent dates, and left the two
 * dates free to disagree about when a concept applied.
 *
 * <p>The level is correlated rather than passed in, because it is a property of
 * the program and the caller only has the program: a subquery keeps the
 * "level means the program's own level" rule from drifting out of sync with the
 * catalog. {@code academic_program.level} is {@code NOT NULL}, so the subquery
 * always yields a value and the level rung always has something to compare
 * against.
 *
 * <p>No {@code ORDER BY}: which row wins is a business rule (most specific
 * first, then most recent), and encoding it in the query would make it
 * invisible to the tests that guard it. The adapter sorts in Java instead.
 */
public interface PaymentRateLookupJpaRepository extends JpaRepository<PaymentRate, UUID> {

	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			  AND r.periodId IS NULL
			  AND r.status = :status
			  AND (r.programId = :programId
			    OR (r.programId IS NULL AND r.level = (
			         SELECT p.level FROM AcademicProgram p WHERE p.id = :programId))
			    OR (r.programId IS NULL AND r.level IS NULL))
			""")
	List<PaymentRate> findRatesPricableForProgram(@Param("conceptId") UUID conceptId,
			@Param("programId") UUID programId, @Param("status") PaymentRateStatus status);
}
