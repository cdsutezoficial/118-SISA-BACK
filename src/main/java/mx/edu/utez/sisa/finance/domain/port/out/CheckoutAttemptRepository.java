package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of {@link CheckoutAttempt}, the generic online-payment trail.
 */
public interface CheckoutAttemptRepository {

	CheckoutAttempt save(CheckoutAttempt attempt);

	Optional<CheckoutAttempt> findByOrderId(String orderId);

	/** Every attempt of a candidate, any charge, any state. */
	List<CheckoutAttempt> findAllByCandidateId(UUID candidateId);

	/** Every attempt still {@code STARTED}, for the nightly sweep. */
	List<CheckoutAttempt> findOpenAttempts();

	/** The candidate's attempts still {@code STARTED}. */
	List<CheckoutAttempt> findOpenAttemptsByCandidateId(UUID candidateId);

}
