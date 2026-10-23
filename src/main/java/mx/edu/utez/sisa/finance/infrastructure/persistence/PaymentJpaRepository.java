package mx.edu.utez.sisa.finance.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentRepositoryAdapter}. Extends
 * {@link JpaRepository} so integration tests can seed rows directly.
 */
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

	/**
	 * Pessimistic write lock on the adeudo, held for the settlement
	 * transaction — the concurrency guard the abono flow relies on.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Payment p WHERE p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

	@Query(value = """
			SELECT p FROM Payment p
			WHERE (:candidateId IS NULL OR p.candidateId = :candidateId)
			  AND (:studentId IS NULL OR p.studentId = :studentId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:conceptCode IS NULL OR p.conceptCode = :conceptCode)
			""",
			countQuery = """
			SELECT COUNT(p) FROM Payment p
			WHERE (:candidateId IS NULL OR p.candidateId = :candidateId)
			  AND (:studentId IS NULL OR p.studentId = :studentId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:conceptCode IS NULL OR p.conceptCode = :conceptCode)
			""")
	Page<Payment> search(@Param("candidateId") UUID candidateId, @Param("studentId") UUID studentId,
			@Param("status") PaymentStatus status, @Param("conceptCode") String conceptCode, Pageable pageable);

	List<Payment> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

	List<Payment> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	List<Payment> findByCandidateIdAndConceptCodeIgnoreCaseOrderByCreatedAtDesc(UUID candidateId, String conceptCode);

	boolean existsByBenefitIdAndStatusIn(UUID benefitId, Collection<PaymentStatus> statuses);
}
