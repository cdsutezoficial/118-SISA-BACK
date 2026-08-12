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
	 * Backs the continuous-rate closing logic (plan section 2/4): finds the
	 * currently-active continuous rate ({@code periodId IS NULL},
	 * {@code validTo IS NULL}) for the EXACT {@code (conceptId, programId,
	 * level)} combination — {@code null} {@code programId}/{@code level} are
	 * matched as their own value in the combination, never as a wildcard.
	 */
	Optional<PaymentRate> findActiveContinuousRate(UUID conceptId, UUID programId, AcademicLevel level);

	/**
	 * Backs the period-scoped uniqueness check (plan section 2/4): {@code true}
	 * if a rate already exists for the EXACT
	 * {@code (conceptId, programId, level, periodId)} combination.
	 */
	boolean existsByExactCombination(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId);

	/**
	 * Full, unpaginated pricing history for a {@code conceptId} (plan section
	 * 4 — no pagination, low expected volume per concept), ordered by
	 * {@code programId}, {@code level}, {@code periodId} ascending, then
	 * {@code validFrom} descending.
	 */
	List<PaymentRate> findHistoryByConceptId(UUID conceptId);
}
