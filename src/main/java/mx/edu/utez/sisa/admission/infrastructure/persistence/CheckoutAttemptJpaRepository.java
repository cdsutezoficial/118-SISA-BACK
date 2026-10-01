package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CheckoutAttempt}.
 */
public interface CheckoutAttemptJpaRepository extends JpaRepository<CheckoutAttempt, UUID> {

	CheckoutAttempt findByOrderId(String orderId);

	List<CheckoutAttempt> findAllByCandidateId(UUID candidateId);

	List<CheckoutAttempt> findByCloseReason(CheckoutAttemptCloseReason closeReason);
}