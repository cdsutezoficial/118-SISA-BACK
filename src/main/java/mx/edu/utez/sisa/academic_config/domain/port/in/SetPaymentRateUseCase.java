package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The ONLY write use case for {@code PaymentRate} — no Update, no Delete
 * (plan section 4: the pricing history is append-only by design). Two
 * independent closing rules apply depending on {@code periodId} (plan
 * section 2):
 * <ul>
 * <li>{@code periodId == null} (continuous rate): closes the currently-active
 * continuous rate for the same {@code (conceptId, programId, level)}
 * combination, then inserts the new row.
 * <li>{@code periodId != null} (period-scoped rate): rejects with
 * {@code DuplicatePaymentRateException} (409) if a rate already exists for
 * the exact same {@code (conceptId, programId, level, periodId)}
 * combination; otherwise just inserts — closes nothing.
 * </ul>
 * Role authorization (ADMIN or PERSONAL_FINANZAS, same pair as
 * {@code PaymentConcept}) is enforced by {@code SecurityFilterConfig}, not
 * here.
 */
public interface SetPaymentRateUseCase {

	PaymentRateResult setRate(SetPaymentRateCommand command);

	/**
	 * @param conceptId required — MUST reference an existing {@code PaymentConcept}
	 * @param programId nullable — MUST reference an existing {@code AcademicProgram} when provided
	 * @param level     nullable — part of the combination key, treated as its own value when null
	 * @param amount    MUST be greater than zero
	 * @param periodId  nullable — MUST reference an existing {@code AcademicPeriod} when provided;
	 *                  {@code null} means continuous, non-null means period-scoped
	 * @param validFrom required
	 */
	record SetPaymentRateCommand(UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount,
			UUID periodId, LocalDate validFrom) {
	}

	record PaymentRateResult(UUID id, UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount,
			UUID periodId, LocalDate validFrom, LocalDate validTo) {
	}
}
