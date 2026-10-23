package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CheckoutAttempt}.
 */
public interface CheckoutAttemptJpaRepository extends JpaRepository<CheckoutAttempt, UUID> {

	CheckoutAttempt findByOrderId(String orderId);

	List<CheckoutAttempt> findAllByCandidateIdOrderByCreatedAtAsc(UUID candidateId);

	List<CheckoutAttempt> findByCloseReasonOrderByCreatedAtAsc(CheckoutAttemptCloseReason closeReason);

	List<CheckoutAttempt> findByCandidateIdAndCloseReasonOrderByCreatedAtAsc(UUID candidateId,
			CheckoutAttemptCloseReason closeReason);

}
