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

	private final FinanceCandidateJpaRepository candidateJpaRepository;

	public PaymentCreditRepositoryAdapter(PaymentCreditJpaRepository jpaRepository,
			FinanceCandidateJpaRepository candidateJpaRepository) {
		this.jpaRepository = jpaRepository;
		this.candidateJpaRepository = candidateJpaRepository;
	}

	/** Fills whose money it is before writing, so every flow that books saldo ties it to the person (D9). */
	@Override
	public PaymentCredit save(PaymentCredit credit) {
		if (credit.getPersonId() == null) {
			personOfCandidate(credit.getCandidateId()).ifPresent(credit::assignPerson);
		}
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

	/** Every credit of the person behind this candidate, whichever ficha produced it. */
	@Override
	public List<PaymentCredit> findByCandidateId(UUID candidateId) {
		return personOfCandidate(candidateId).map(jpaRepository::findByPerson)
				.orElseGet(() -> jpaRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId));
	}

	@Override
	public List<PaymentCredit> findByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
	}

	/** The available saldo of the person behind this candidate, whichever ficha produced it. */
	@Override
	public List<PaymentCredit> findAvailableByCandidateId(UUID candidateId) {
		return personOfCandidate(candidateId).map(jpaRepository::findAvailableByPerson)
				.orElseGet(() -> jpaRepository.findByCandidateIdAndStatusOrderByCreatedAtAsc(candidateId,
						CreditStatus.AVAILABLE));
	}

	@Override
	public List<PaymentCredit> findAvailableByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdAndStatusOrderByCreatedAtAsc(studentId, CreditStatus.AVAILABLE);
	}

	@Override
	public Optional<PaymentCredit> findByExternalOrderId(String externalOrderId) {
		return jpaRepository.findByExternalOrderId(externalOrderId);
	}

	@Override
	public List<PaymentCredit> findOverpayments() {
		return jpaRepository.findByExternalOrderIdIsNotNullOrderByCreatedAtDesc();
	}

	private Optional<UUID> personOfCandidate(UUID candidateId) {
		return candidateId == null ? Optional.empty()
				: candidateJpaRepository.findById(candidateId).map(candidate -> candidate.getPersonId());
	}
}
