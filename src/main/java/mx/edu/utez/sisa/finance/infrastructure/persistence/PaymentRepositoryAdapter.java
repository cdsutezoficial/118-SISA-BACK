package mx.edu.utez.sisa.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentRepository} adapter delegating to
 * {@link PaymentJpaRepository}. Results sort by {@code createdAt} descending
 * then {@code id} ascending for deterministic pagination — newest debt first.
 */
@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

	private final PaymentJpaRepository jpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Payment save(Payment payment) {
		return jpaRepository.save(payment);
	}

	@Override
	public Optional<Payment> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	/**
	 * The {@code SELECT ... FOR UPDATE} waits for whoever holds the row, but if this
	 * transaction already read the row, Hibernate hands back that earlier copy from
	 * the persistence context, not the row as the lock found it. Two confirmations of
	 * one charge 0.26 s apart both saw it unpaid that way and both booked an abono.
	 * The refresh re-reads it under the same lock, so the caller sees the last commit.
	 */
	@Override
	public Optional<Payment> findByIdForUpdate(UUID id) {
		return jpaRepository.findByIdForUpdate(id).map(payment -> {
			entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
			return payment;
		});
	}

	@Override
	public PaymentSearchPage search(PaymentSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<Payment> page = jpaRepository.search(criteria.candidateId(), criteria.studentId(), criteria.status(),
				criteria.conceptCode(), pageRequest);
		return new PaymentSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}

	@Override
	public List<Payment> findByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
	}

	@Override
	public List<Payment> findByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
	}

	@Override
	public List<Payment> findByCandidateIdAndConceptCode(UUID candidateId, String conceptCode) {
		return jpaRepository.findByCandidateIdAndConceptCodeIgnoreCaseOrderByCreatedAtDesc(candidateId, conceptCode);
	}

	@Override
	public boolean isBenefitSettled(UUID benefitId) {
		return jpaRepository.existsByBenefitIdAndStatusIn(benefitId, List.of(
				PaymentStatus.PAID,
				PaymentStatus.PARTIAL,
				PaymentStatus.EXEMPT));
	}
}
