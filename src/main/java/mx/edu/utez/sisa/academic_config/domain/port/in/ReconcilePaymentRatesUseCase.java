package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentRateDraft;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The ONLY write use case for {@code PaymentRate}. It takes the concept's
 * COMPLETE rate set and reconciles it against what is already stored, in one
 * transaction — there is no single-rate create and no delete, because the
 * catalog only ever knows one price per destination and the history is
 * append-only.
 *
 * <p>
 * Why a set and not a single rate: the previous endpoint took one
 * {@code (programId, level)} at a time, so the form had to loop. A loop is not
 * atomic, and a failure halfway through left the concept priced for some
 * careers and not others with no way to tell from the outside. Worse, for a
 * {@code PERIODIC_QUOTA} concept that partial state is not merely untidy, it
 * is a student at the counter with no number to charge. Sending the whole set
 * once makes "all careers are priced" a property the transaction either has or
 * refuses, and lets the caller validate the invariant before anything is
 * written.
 *
 * <p>
 * The rules, per destination in the payload:
 * <ul>
 * <li>amount unchanged — the stored row is left alone. No row churn, no fake
 * history entry for a save that changed nothing.
 * <li>amount changed — the row in force is deactivated and a new ACTIVE one is
 * inserted, so the old price stays answerable.
 * <li>destination absent from the payload — deactivated, but only for concept
 * types where that is allowed. A {@code PERIODIC_QUOTA} rejects it outright
 * ({@code IncompletePaymentRateSetException}); for any other type a rate goes
 * INACTIVE because its program stopped being ACTIVE.
 * <li>destination repeated in the payload — rejected
 * ({@code DuplicatePaymentRateException}); the key has to be unambiguous or
 * "which one won" is decided by list order.
 * </ul>
 *
 * <p>
 * Role authorization (ADMIN or PERSONAL_FINANZAS, same pair as
 * {@code PaymentConcept}) is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ReconcilePaymentRatesUseCase {

	List<PaymentRateResult> reconcileRates(ReconcilePaymentRatesCommand command);

	/**
	 * @param conceptId required — MUST reference an existing {@code PaymentConcept}
	 * @param rates     the complete desired set. Destinations absent from it are
	 *                  deactivated, subject to the concept type's rule
	 */
	record ReconcilePaymentRatesCommand(UUID conceptId, List<PaymentRateDraft> rates) {
	}

	/**
	 * @param status whether this row is the one in force, or history
	 * @param createdAt when this amount entered the history; audit only
	 */
	record PaymentRateResult(UUID id, UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount,
			UUID periodId, PaymentRateStatus status, LocalDateTime createdAt) {
	}
}