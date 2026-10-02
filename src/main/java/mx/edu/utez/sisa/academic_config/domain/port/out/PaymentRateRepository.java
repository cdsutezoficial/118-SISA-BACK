package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentRate} — its own repository, not
 * encapsulated inside {@code PaymentConceptRepository} (plan section 5).
 */
public interface PaymentRateRepository {

	PaymentRate save(PaymentRate rate);

	/**
	 * The row in force for the EXACT
	 * {@code (conceptId, programId, level, periodId)} combination — every
	 * nullable key column is matched as its own value, never as a wildcard.
	 */
	Optional<PaymentRate> findActive(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId);

	/**
	 * Every row for a {@code conceptId}, current and historical, in an order
	 * that puts each destination's current price first.
	 */
	List<PaymentRate> findHistoryByConceptId(UUID conceptId);

	/**
	 * Only the rows currently in force for a {@code conceptId}, with no ordering
	 * guarantee beyond the database's.
	 *
	 * <p>
	 * Separate from {@link #findHistoryByConceptId} rather than a flag on it
	 * because the two callers want opposite things: the editor and the rate
	 * endpoint want the whole history to render, while the type-change guard in
	 * {@code UpdatePaymentConceptUseCaseImpl} needs to answer "which careers does
	 * this concept price right now" and must not have to filter out superseded
	 * rows itself — a caller that forgot the filter would count a retired price as
	 * coverage and pass a check it should have failed.
	 */
	List<PaymentRate> findActiveByConceptId(UUID conceptId);
}