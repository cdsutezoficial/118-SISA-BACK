package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentEntryRepositoryAdapter}.
 * Append-only: no update/delete selectors exist on purpose.
 */
public interface PaymentEntryJpaRepository extends JpaRepository<PaymentEntry, UUID> {

	List<PaymentEntry> findByPaymentIdOrderByPaidAtAsc(UUID paymentId);

	/**
	 * Returns {@code null} when the payment has no abonos — the adapter maps
	 * that to zero. No {@code COALESCE} in the query so the SQL stays a plain
	 * {@code SUM} portable across the test (H2) and production (MySQL) dialects.
	 */
	@Query("SELECT SUM(e.amount) FROM PaymentEntry e WHERE e.paymentId = :paymentId")
	BigDecimal sumAmountByPaymentId(@Param("paymentId") UUID paymentId);
}
