package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Moves a charge to another program when the payer changes careers.
 *
 * <p>Two cases, decided by the caller from the charge's state:
 * <ul>
 * <li><b>unpaid</b> — {@link #reassign}: the same charge takes the new
 * concept/program and keeps its discount, so the next quote is the new tariff
 * minus that discount;</li>
 * <li><b>paid</b> — {@link #rebill}: the paid charge stays as history and a
 * new charge is raised at the new tariff with no discount (the old one was
 * used when paying). What was paid becomes saldo a favor and is applied to
 * the new charge: what is left to pay is the difference, and what is left
 * over stays as saldo a favor.</li>
 * </ul>
 * The {@code preview*} methods compute the same numbers without writing.
 */
public interface ChangeChargeProgramUseCase {

	/** The charge priced as if it already belonged to the new program. */
	DebtQuote previewReassign(UUID paymentId, String conceptCode, UUID programId);

	void reassign(UUID paymentId, String conceptCode, UUID programId);

	RebillOutcome previewRebill(UUID fromPaymentId, String conceptCode, UUID programId);

	RebillOutcome rebill(RebillCommand command);

	/**
	 * @param fromPaymentId the charge being left: the paid one, or a previous
	 *                      career-change charge still unpaid (it is cancelled
	 *                      and what it had received goes back to saldo a favor)
	 * @param dueAt         deadline of the new charge
	 * @param reference     reference the payer quotes for the new charge
	 * @param actingUserId  who made the change
	 */
	record RebillCommand(UUID fromPaymentId, String conceptCode, UUID programId, Instant dueAt, String reference,
			UUID actingUserId) {
	}

	/**
	 * @param paymentId     the new charge; {@code null} in a preview
	 * @param rateId        the tariff row of the new program
	 * @param newAmount     the new program's tariff, with no discount
	 * @param alreadyPaid   what the charge being left had received
	 * @param otherCredit   saldo a favor the person already had, also applied
	 * @param creditApplied how much saldo a favor covers of the new charge
	 * @param toPay         what is left to pay of the new charge
	 * @param creditLeft    saldo a favor left over after covering it
	 * @param status        the new charge's status ({@code PAID} when covered)
	 */
	record RebillOutcome(UUID paymentId, UUID rateId, BigDecimal newAmount, BigDecimal alreadyPaid,
			BigDecimal otherCredit, BigDecimal creditApplied, BigDecimal toPay, BigDecimal creditLeft,
			PaymentStatus status) {
	}
}
