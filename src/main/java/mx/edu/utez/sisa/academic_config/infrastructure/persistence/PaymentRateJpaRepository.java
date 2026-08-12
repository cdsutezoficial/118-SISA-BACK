package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentRateRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows) — same convention as every
 * other {@code JpaRepository} in this module.
 *
 * <p>
 * Both {@link #findActiveContinuousRate} and {@link #existsByExactCombination}
 * use the {@code (:param IS NULL AND column IS NULL) OR column = :param}
 * pattern for {@code programId}/{@code level} so a {@code null} caller value
 * matches only rows where that column is ALSO {@code null} — plain JPQL
 * {@code =} never matches {@code NULL}, and treating null as a wildcard here
 * would incorrectly collapse distinct combinations (plan section 2/4: null is
 * its own value in the combination key, not "any").
 */
public interface PaymentRateJpaRepository extends JpaRepository<PaymentRate, UUID> {

	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			  AND ((:programId IS NULL AND r.programId IS NULL) OR r.programId = :programId)
			  AND ((:level IS NULL AND r.level IS NULL) OR r.level = :level)
			  AND r.periodId IS NULL
			  AND r.validTo IS NULL
			""")
	Optional<PaymentRate> findActiveContinuousRate(@Param("conceptId") UUID conceptId,
			@Param("programId") UUID programId, @Param("level") AcademicLevel level);

	@Query("""
			SELECT COUNT(r) > 0 FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			  AND ((:programId IS NULL AND r.programId IS NULL) OR r.programId = :programId)
			  AND ((:level IS NULL AND r.level IS NULL) OR r.level = :level)
			  AND r.periodId = :periodId
			""")
	boolean existsByExactCombination(@Param("conceptId") UUID conceptId, @Param("programId") UUID programId,
			@Param("level") AcademicLevel level, @Param("periodId") UUID periodId);

	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			ORDER BY r.programId ASC, r.level ASC, r.periodId ASC, r.validFrom DESC
			""")
	List<PaymentRate> findHistoryByConceptId(@Param("conceptId") UUID conceptId);
}
