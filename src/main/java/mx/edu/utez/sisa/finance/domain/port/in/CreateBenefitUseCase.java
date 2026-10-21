package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates a discount ({@code PaymentBenefit}) for a
 * candidato/estudiante on a concept with vigencia (R5, Fase 2 §5.1). A mode
 * {@code FULL} is the 100 %/exención case; there is no separate exemption type.
 */
public interface CreateBenefitUseCase {

	BenefitView createBenefit(CreateBenefitCommand command);

	/**
	 * @param candidateId     aspirante anchor — exactly one of
	 *                        {@code candidateId}/{@code studentId} is required
	 * @param studentId       student anchor
	 * @param conceptCode     required — catalog code or cross-context code
	 * @param periodId        optional — anchor; {@code null} = any period
	 * @param generationId    optional — anchor; {@code null} = any generation
	 * @param mode            required — {@code PERCENT}/{@code AMOUNT}/{@code FULL}
	 * @param percent         required for {@code PERCENT}, else {@code null}
	 * @param amount          required for {@code AMOUNT}, else {@code null}
	 * @param validFrom       required — inclusive start of the window
	 * @param validTo         required — inclusive end of the window
	 * @param reason          required — why it was granted (audit trail)
	 * @param grantedById     required — the acting user (from the JWT)
	 * @param grantedByName   required — display name for the receipt/statement
	 */
	record CreateBenefitCommand(UUID candidateId, UUID studentId, String conceptCode, UUID periodId, UUID generationId,
			BenefitMode mode, BigDecimal percent, BigDecimal amount, LocalDate validFrom, LocalDate validTo, String reason,
			UUID grantedById, String grantedByName) {
	}
}
