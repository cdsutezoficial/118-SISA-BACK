package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentBenefitRepositoryAdapter}.
 */
public interface PaymentBenefitJpaRepository extends JpaRepository<PaymentBenefit, UUID> {

	/**
	 * Candidate discounts for the quoter. Only ACTIVE rows; window and anchor
	 * matching live in the entity's {@code appliesTo}, not in the query.
	 */
	List<PaymentBenefit> findByCandidateIdAndConceptCodeIgnoreCaseAndStatus(UUID candidateId, String conceptCode,
			BenefitStatus status);

	/**
	 * Student discounts for the quoter (same contract as the candidate variant).
	 */
	List<PaymentBenefit> findByStudentIdAndConceptCodeIgnoreCaseAndStatus(UUID studentId, String conceptCode,
			BenefitStatus status);

	/**
	 * Filterable query backing the manager list and the discount report. Every
	 * criterion is optional ({@code null} = "any"); the concept-code match is
	 * case-insensitive and {@code createdFrom}/{@code createdTo} bound the
	 * grant date.
	 */
	@Query("""
			SELECT b FROM PaymentBenefit b
			WHERE (:candidateId IS NULL OR b.candidateId = :candidateId)
			  AND (:studentId IS NULL OR b.studentId = :studentId)
			  AND (:conceptCode IS NULL OR LOWER(b.conceptCode) = LOWER(:conceptCode))
			  AND (:status IS NULL OR b.status = :status)
			  AND (:createdFrom IS NULL OR b.createdAt >= :createdFrom)
			  AND (:createdTo IS NULL OR b.createdAt <= :createdTo)
			ORDER BY b.createdAt DESC
			""")
	List<PaymentBenefit> search(@Param("candidateId") UUID candidateId, @Param("studentId") UUID studentId,
			@Param("conceptCode") String conceptCode, @Param("status") BenefitStatus status,
			@Param("createdFrom") Instant createdFrom, @Param("createdTo") Instant createdTo);
}
