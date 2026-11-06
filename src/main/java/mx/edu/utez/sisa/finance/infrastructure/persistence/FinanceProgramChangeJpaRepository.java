package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read-only-by-usage lookup over admission's {@code candidate_program_change}
 * history, backing {@link ChargeReplacementQueryAdapter}. Finance only reads
 * which charge replaced which; it never writes the history.
 */
public interface FinanceProgramChangeJpaRepository extends JpaRepository<CandidateProgramChange, UUID> {

	/**
	 * The charges raised by paid career changes that left {@code paymentId}
	 * behind, newest first. An unpaid change moves the same charge
	 * ({@code newPaymentId} is null), so it does not count.
	 */
	@Query("SELECT c.newPaymentId FROM CandidateProgramChange c WHERE c.fichaPaymentId = :paymentId"
			+ " AND c.wasPaid = true AND c.newPaymentId IS NOT NULL ORDER BY c.changedAt DESC")
	List<UUID> findReplacingChargeIds(@Param("paymentId") UUID paymentId);
}
