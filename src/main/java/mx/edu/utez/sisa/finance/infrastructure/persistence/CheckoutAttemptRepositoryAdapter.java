package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
		return Optional.ofNullable(jpaRepository.findByOrderId(orderId));
	}

	@Override
	public List<CheckoutAttempt> findAllByCandidateId(UUID candidateId) {
		return jpaRepository.findAllByCandidateIdOrderByCreatedAtAsc(candidateId);
	}

	@Override
	public List<CheckoutAttempt> findOpenAttempts() {
		return jpaRepository.findByCloseReasonOrderByCreatedAtAsc(CheckoutAttemptCloseReason.STARTED);
	}

	@Override
	public List<CheckoutAttempt> findOpenAttemptsByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateIdAndCloseReasonOrderByCreatedAtAsc(candidateId,
				CheckoutAttemptCloseReason.STARTED);
	}

}
