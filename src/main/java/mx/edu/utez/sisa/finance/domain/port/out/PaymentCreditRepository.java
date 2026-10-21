package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentCredit} (saldo a favor, Fase 2). The
 * write side is {@code save}; a credit is never hard-deleted (voiding is a
 * status transition on the entity itself).
 */
public interface PaymentCreditRepository {

	PaymentCredit save(PaymentCredit credit);

	Optional<PaymentCredit> findById(UUID id);

	/**
	 * Pessimistic write lock on the credit row, held while it is applied, so
	 * two concurrent applications cannot both consume the same saldo a favor
	 * — same pattern as {@code PaymentRepository#findByIdForUpdate}.
	 */
	Optional<PaymentCredit> findByIdForUpdate(UUID id);

	/**
	 * Every credit of an aspirante, newest first — backing {@code GET
	 * /finance/credits}.
	 */
	List<PaymentCredit> findByCandidateId(UUID candidateId);

	/**
	 * Every credit of a student, newest first.
	 */
	List<PaymentCredit> findByStudentId(UUID studentId);

	/**
	 * The {@code AVAILABLE} credits of an aspirante, oldest first. The apply
	 * flow walks these to cover an adeudo; anything left over stays available.
	 */
	List<PaymentCredit> findAvailableByCandidateId(UUID candidateId);

	/**
	 * The {@code AVAILABLE} credits of a student, oldest first.
	 */
	List<PaymentCredit> findAvailableByStudentId(UUID studentId);
}
