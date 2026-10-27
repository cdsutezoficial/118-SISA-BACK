package mx.edu.utez.sisa.finance.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentCreditRepositoryAdapter}.
 */
public interface PaymentCreditJpaRepository extends JpaRepository<PaymentCredit, UUID> {

	/**
	 * Pessimistic write lock on the credit, held for the apply transaction so
	 * two concurrent applications cannot both spend the same saldo a favor.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM PaymentCredit c WHERE c.id = :id")
	Optional<PaymentCredit> findByIdForUpdate(@Param("id") UUID id);

	List<PaymentCredit> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

	List<PaymentCredit> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	List<PaymentCredit> findByCandidateIdAndStatusOrderByCreatedAtAsc(UUID candidateId, CreditStatus status);

	List<PaymentCredit> findByStudentIdAndStatusOrderByCreatedAtAsc(UUID studentId, CreditStatus status);
}
