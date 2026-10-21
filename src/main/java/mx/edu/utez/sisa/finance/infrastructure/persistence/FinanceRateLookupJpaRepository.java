package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read-only Spring Data lookup over {@code PaymentRate} (lives in
 * {@code academic_config}) backing {@link ConceptRateQueryAdapter}.
 *
 * <p>Mirrors {@code admission.PaymentRateLookupJpaRepository}: returns every
 * continuous, in-force rate of a concept that could price a program, so the
 * adapter can pick between the three rungs (exact program → the program's
 * level → neither). {@code periodId IS NULL} keeps period-scoped rows out —
 * they price a specific period, not a continuous charge.
 */
public interface FinanceRateLookupJpaRepository extends JpaRepository<PaymentRate, UUID> {

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
