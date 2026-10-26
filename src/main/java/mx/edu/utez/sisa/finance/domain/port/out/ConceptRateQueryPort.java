package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port over {@code PaymentRate}'s pricing ladder (catalog lives
 * in {@code academic_config}).
 *
 * <p>Resolves the {@link PaymentRateStatus} row in force for a
 * {@code (conceptId, programId, level)} combination, walking the three-rung
 * ladder most-specific-first: the rate bound to the exact program, then the
 * one bound to the program's level, then the one bound to neither. Period
 * rates ({@code periodId != null}) are excluded — a row pinned to one period
 * does not price a continuous charge.
 *
 * <p>Empty is NOT a fallback signal: the caller must fail
 * ({@code NoRateAvailableException}) rather than invent an amount.
 */
public interface ConceptRateQueryPort {

	/**
	 * The ACTIVE rate amount for the concept, most specific rung first.
	 *
	 * @param conceptId required
	 * @param programId required — the ladder is walked against this program
	 * @param level     optional — extra narrowing kept for future period-scoped
	 *                  pricing; the current ladder derives the level from the
	 *                  program itself
	 */
	Optional<BigDecimal> findActiveAmount(UUID conceptId, UUID programId, AcademicLevel level);

	/**
	 * The same winning rate as {@link #findActiveAmount}, carrying the
	 * {@code payment_rate} row id so a frozen quote can point at the exact
	 * catalog row that priced it.
	 */
	Optional<ActiveRate> findActiveRate(UUID conceptId, UUID programId);

	/**
	 * @param id     the {@code payment_rate} row id
	 * @param amount the amount that row prices the concept with
	 */
	record ActiveRate(UUID id, BigDecimal amount) {
	}
}
