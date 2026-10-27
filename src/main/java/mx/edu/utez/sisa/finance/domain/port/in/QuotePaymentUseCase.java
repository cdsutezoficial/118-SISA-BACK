package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cotizador (R3): resolves {@code (persona, concepto, periodo, generación,
 * programa, nivel)} into the catalog rate, the discount in force, and the
 * final amount due. <b>Does not persist</b> — {@link CreatePaymentUseCase}
 * freezes the quotation into a {@code Payment}.
 */
public interface QuotePaymentUseCase {

	QuoteResult quote(QuoteCommand command);

	/**
	 * @param candidateId   the aspirante anchor — exactly one of
	 *                      {@code candidateId}/{@code studentId} is required
	 * @param studentId     the student anchor
	 * @param conceptCode   required — the catalog {@code code} (or an
	 *                      cross-context code such as {@code ADMISSION_FICHA})
	 * @param periodId      optional — charge anchor; {@code null} for fichas
	 * @param generationId  optional — charge anchor
	 * @param programId     required — the rate ladder is walked against this
	 *                      program
	 * @param level         optional — informational; the ladder derives the
	 *                      level from the program
	 * @param onDate        required — the business date the concept window and
	 *                      the discount window are evaluated against
	 */
	record QuoteCommand(UUID candidateId, UUID studentId, String conceptCode, UUID periodId, UUID generationId,
			UUID programId, AcademicLevel level, LocalDate onDate) {
	}

	/**
	 * @param conceptType    the quoted concept's type, denormalized so the
	 *                       adeudo can store it without re-querying the catalog
	 * @param originalAmount the catalog rate before any discount
	 * @param discountAmount the discount applied (0 when none) — already
	 *                       rounded to MXN cents
	 * @param amountDue      {@code originalAmount - discountAmount}: the only
	 *                       amount collected
	 * @param benefit        the discount that was applied, when one was
	 * @param exempt         {@code true} when {@code amountDue} is zero because
	 *                       a FULL benefit covers the whole charge
	 */
	record QuoteResult(PaymentConceptType conceptType, BigDecimal originalAmount, BigDecimal discountAmount,
			BigDecimal amountDue, BenefitSummary benefit, boolean exempt) {
	}

	/**
	 * The applied discount, projected for the quote/payment response and the
	 * statement. {@code null} fields depend on {@code mode}.
	 */
	record BenefitSummary(UUID id, BenefitMode mode, BigDecimal percent, BigDecimal amount, String reason,
			String grantedByName) {
	}
}
