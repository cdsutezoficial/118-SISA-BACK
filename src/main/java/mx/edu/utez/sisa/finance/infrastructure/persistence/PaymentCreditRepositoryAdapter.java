package mx.edu.utez.sisa.finance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
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

	@PersistenceContext
	private EntityManager entityManager;

	private final FinanceCandidateJpaRepository candidateJpaRepository;

	public PaymentCreditRepositoryAdapter(PaymentCreditJpaRepository jpaRepository,
			FinanceCandidateJpaRepository candidateJpaRepository) {
		this.jpaRepository = jpaRepository;
		this.candidateJpaRepository = candidateJpaRepository;
	}

	@Override
	public PaymentCredit save(PaymentCredit credit) {
		return jpaRepository.save(credit);
	}

	@Override
	public Optional<PaymentCredit> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	/** Same as {@code PaymentRepositoryAdapter#findByIdForUpdate}: re-read under the lock. */
	@Override
	public Optional<PaymentCredit> findByIdForUpdate(UUID id) {
		return jpaRepository.findByIdForUpdate(id).map(credit -> {
			entityManager.refresh(credit, LockModeType.PESSIMISTIC_WRITE);
			return credit;
		});
	}

	/**
	 * Every credit of the person behind this candidate, whichever ficha produced it
	 * (D9). The person is the CURP: it does not change between fichas, so every
	 * candidacy that carries it shares the saldo.
	 */
	@Override
	public List<PaymentCredit> findByCandidateId(UUID candidateId) {
		return candidateId == null ? List.of()
				: jpaRepository.findByCandidateIdInOrderByCreatedAtDesc(fichasOfPerson(candidateId));
	}

	@Override
	public List<PaymentCredit> findByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
	}

	/** The available saldo of the person behind this candidate, whichever ficha produced it. */
	@Override
	public List<PaymentCredit> findAvailableByCandidateId(UUID candidateId) {
		return candidateId == null ? List.of()
				: jpaRepository.findByCandidateIdInAndStatusOrderByCreatedAtAsc(fichasOfPerson(candidateId),
						CreditStatus.AVAILABLE);
	}

	@Override
	public List<PaymentCredit> findAvailableOwnByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateIdAndStatusOrderByCreatedAtAsc(candidateId, CreditStatus.AVAILABLE);
	}

	@Override
	public List<PaymentCredit> findAvailableByStudentId(UUID studentId) {
		return jpaRepository.findByStudentIdAndStatusOrderByCreatedAtAsc(studentId, CreditStatus.AVAILABLE);
	}

	@Override
	public Optional<PaymentCredit> findByExternalOrderId(String externalOrderId) {
		return jpaRepository.findByExternalOrderId(externalOrderId);
	}


	/**
	 * The candidate records of the person behind this one: every ficha with the same
	 * CURP, or just this one when it has none. Read through finance's own lookup over
	 * the candidate table, like {@link AnchorQueryAdapter}.
	 */
	private List<UUID> fichasOfPerson(UUID candidateId) {
		List<UUID> ids = candidateJpaRepository.findById(candidateId)
				.map(candidate -> candidate.getCurp())
				.filter(curp -> curp != null && !curp.isBlank())
				.map(candidateJpaRepository::findIdsByCurp)
				.orElse(List.of());
		return ids.isEmpty() ? List.of(candidateId) : ids;
	}
}
