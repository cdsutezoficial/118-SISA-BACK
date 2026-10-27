package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only Spring Data lookup over {@code PaymentConcept} (lives in
 * {@code academic_config}) backing {@link ConceptQueryAdapter} — same
 * cross-context pattern as {@code admission.PaymentConceptLookupJpaRepository}.
 * {@code finance} reads the catalog; it does not own it.
 */
public interface FinanceConceptJpaRepository extends JpaRepository<PaymentConcept, UUID> {

	/**
	 * Any status — used to tell "no existe" from "no está a la venta". The
	 * sellable/not-sellable distinction is applied by the adapter.
	 */
	Optional<PaymentConcept> findByCodeIgnoreCase(String code);

	/**
	 * ACTIVE only — the availability window is still checked in the adapter,
	 * against the caller's business date.
	 */
	Optional<PaymentConcept> findByCodeIgnoreCaseAndStatus(String code, PaymentConceptStatus status);
}
