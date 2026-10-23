package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link Payment}, mirroring
 * {@code academic_config.PaymentAreaRepository}'s shape.
 *
 * <p>{@code search} backs the estado-de-cuenta list; the write side is just
 * {@code save} — adeudos are never hard-deleted (voiding is a status
 * transition on the entity itself).
 */
public interface PaymentRepository {

	Payment save(Payment payment);

	Optional<Payment> findById(UUID id);

	/**
	 * Same lookup as {@link #findById(UUID)} but taking a pessimistic write
	 * lock on the row for the duration of the surrounding transaction. The
	 * settlement flow uses it so two concurrent abonos serialize on the adeudo
	 * instead of both reading the same abono total and both writing a
	 * half-updated row (Fase 1 — "no leer-modificar-escribir sin lock").
	 */
	Optional<Payment> findByIdForUpdate(UUID id);

	/**
	 * Filterable, paginated query backing {@code GET /finance/payments}.
	 * Results are sorted by {@code createdAt} descending then {@code id}
	 * ascending for deterministic pagination — newest debt first.
	 */
	PaymentSearchPage search(PaymentSearchCriteria criteria);

	/**
	 * Every payment of an aspirante, newest first, with no pagination — the
	 * account statement of a candidate reads the whole history at once.
	 */
	List<Payment> findByCandidateId(UUID candidateId);

	/**
	 * Every payment of a student, newest first — the account statement of an
	 * enrolled student.
	 */
	List<Payment> findByStudentId(UUID studentId);

	/**
	 * The payments of an aspirante on one concept, newest first — the
	 * retroactive discount flow reads it to find adeudos already settled
	 * without the discount (R12), and the ficha flow reads {@code
	 * ADMISSION_FICHA} to find the row created at registration.
	 */
	List<Payment> findByCandidateIdAndConceptCode(UUID candidateId, String conceptCode);

	/**
	 * Whether a charge that already received money (or was exempted) was
	 * priced with this benefit — the point after which the benefit is history.
	 */
	boolean isBenefitSettled(UUID benefitId);

	record PaymentSearchCriteria(UUID candidateId, UUID studentId, PaymentStatus status, String conceptCode, int page,
			int size) {
	}

	record PaymentSearchPage(List<Payment> content, long totalElements, int totalPages) {
	}
}
