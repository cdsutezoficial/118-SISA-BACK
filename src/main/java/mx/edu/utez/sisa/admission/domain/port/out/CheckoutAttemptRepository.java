package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link CheckoutAttempt}, the history of payment
 * attempts that allows reconciliation (§3.7).
 */
public interface CheckoutAttemptRepository {

	CheckoutAttempt save(CheckoutAttempt attempt);

	Optional<CheckoutAttempt> findByOrderId(String orderId);

	List<CheckoutAttempt> findAllByCandidateId(UUID candidateId);

	List<CheckoutAttempt> findOpenAttempts();
}