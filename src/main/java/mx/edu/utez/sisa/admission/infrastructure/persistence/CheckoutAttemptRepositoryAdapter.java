package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed adapter for {@link CheckoutAttemptRepository}.
 */
@Component
public class CheckoutAttemptRepositoryAdapter implements CheckoutAttemptRepository {

	private final CheckoutAttemptJpaRepository jpaRepository;

	public CheckoutAttemptRepositoryAdapter(CheckoutAttemptJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public CheckoutAttempt save(CheckoutAttempt attempt) {
		return jpaRepository.save(attempt);
	}

	@Override
	public Optional<CheckoutAttempt> findByOrderId(String orderId) {
		CheckoutAttempt attempt = jpaRepository.findByOrderId(orderId);
		return Optional.ofNullable(attempt);
	}

	@Override
	public List<CheckoutAttempt> findAllByCandidateId(UUID candidateId) {
		return jpaRepository.findAllByCandidateId(candidateId);
	}

	@Override
	public List<CheckoutAttempt> findOpenAttempts() {
		return jpaRepository.findByCloseReason(CheckoutAttemptCloseReason.STARTED);
	}
}