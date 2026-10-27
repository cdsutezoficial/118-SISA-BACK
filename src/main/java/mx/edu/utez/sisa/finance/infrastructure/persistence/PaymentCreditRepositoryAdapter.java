package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentCreditRepository} adapter. The available list comes
 * back oldest-first, which is the order the apply flow consumes credits in.
 */
@Component
public class PaymentCreditRepositoryAdapter implements PaymentCreditRepository {

	private final PaymentCreditJpaRepository jpaRepository;

	public PaymentCreditRepositoryAdapter(PaymentCreditJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentCredit save(PaymentCredit credit) {
		return jpaRepository.save(credit);
	}

	@Override
	public Optional<PaymentCredit> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<PaymentCredit> findByIdForUpdate(UUID id) {
		return jpaRepository.findByIdForUpdate(id);
	}

	@Override
	public List<PaymentCredit> findByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
	}

	@Override
	public List<PaymentCredit> findByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
	}

	@Override
	public List<PaymentCredit> findAvailableByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateIdAndStatusOrderByCreatedAtAsc(candidateId, CreditStatus.AVAILABLE);
	}

	@Override
	public List<PaymentCredit> findAvailableByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdAndStatusOrderByCreatedAtAsc(studentId, CreditStatus.AVAILABLE);
	}
}
