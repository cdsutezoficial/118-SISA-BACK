package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
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
 * {@link #findActiveByConceptId} uses the
 * {@code (:param IS NULL AND column IS NULL) OR column = :param} pattern for
 * {@code programId}/{@code level} so a {@code null} caller value matches only
 * rows where that column is ALSO {@code null} — plain JPQL {@code =} never
 * matches {@code NULL}, and treating null as a wildcard here would incorrectly
 * collapse distinct combinations (null is its own value in the combination
 * key, not "any").
 *
 * <p>
 * "Which row is in force" is {@code status = ACTIVE} rather than a
 * {@code validTo IS NULL} test. Those agree today, but the date version needed
 * a caller-supplied {@code validFrom} to decide anything, so an unchanged save
 * had to invent a date to close nothing, and a back-dated correction was
 * indistinguishable from a future one.
 */
public interface PaymentRateJpaRepository extends JpaRepository<PaymentRate, UUID> {

	/**
	 * The row in force for one exact destination. All three key columns use the
	 * null-safe pattern so a {@code null} matches only a {@code NULL} column —
	 * plain JPQL {@code =} never matches {@code NULL}, and treating null as a
	 * wildcard would collapse "priced for no particular career" into "priced for
	 * every career", which is a different price.
 */
	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			  AND ((:programId IS NULL AND r.programId IS NULL) OR r.programId = :programId)
			  AND ((:level IS NULL AND r.level IS NULL) OR r.level = :level)
			  AND ((:periodId IS NULL AND r.periodId IS NULL) OR r.periodId = :periodId)
			  AND r.status = :status
			""")
	Optional<PaymentRate> findActive(@Param("conceptId") UUID conceptId, @Param("programId") UUID programId,
			@Param("level") AcademicLevel level, @Param("periodId") UUID periodId,
			@Param("status") PaymentRateStatus status);

	/**
	 * Every row for a concept — the ACTIVE one and all superseded ones — ordered
	 * so the current price of each destination leads and its history follows.
	 * {@code createdAt DESC} is what makes "last changed at" answerable, and it
	 * is stable across rows written in the same millisecond only because a
	 * reconciliation never writes two rows for one destination at once.
	 */
	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			ORDER BY r.programId ASC, r.level ASC, r.periodId ASC, r.createdAt DESC
			""")
	List<PaymentRate> findHistoryByConceptId(@Param("conceptId") UUID conceptId);

	/**
	 * The rows in force for a concept, and nothing else. Backs the coverage check
	 * that guards a concept being turned into a {@code PERIODIC_QUOTA}, which
	 * would otherwise count a superseded row as a live price.
	 */
	@Query("""
			SELECT r FROM PaymentRate r
			WHERE r.conceptId = :conceptId
			  AND r.status = :status
			""")
	List<PaymentRate> findActiveByConceptId(@Param("conceptId") UUID conceptId,
			@Param("status") PaymentRateStatus status);
}