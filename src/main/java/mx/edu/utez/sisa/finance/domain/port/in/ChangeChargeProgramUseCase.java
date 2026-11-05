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
 * new charge is raised at the new tariff minus the discount the person
 * carries (D6: the discount travels, re-quoted on the new tariff). Only money
 * moves: what was paid, less what a retroactive discount already returned as
 * saldo, becomes saldo a favor and is applied to
 * the new charge: what is left to pay is the difference, and what is left
 * over stays as saldo a favor.</li>
 * </ul>
 * The {@code preview*} methods compute the same numbers without writing.
 */
public interface ChangeChargeProgramUseCase {

	/** The charge priced as if it already belonged to the new program. */
	DebtQuote previewReassign(UUID paymentId, String conceptCode, UUID programId);

	default void reassign(UUID paymentId, String conceptCode, UUID programId) {
		reassign(paymentId, conceptCode, programId, null, null);
	}

	/** F5: with the new career's period and generation; {@code null} keeps the current one. */
	void reassign(UUID paymentId, String conceptCode, UUID programId, UUID periodId, UUID generationId);

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
			UUID actingUserId, UUID periodId, UUID generationId) {

		/** Keeps the period and generation of the charge being left. */
		public RebillCommand(UUID fromPaymentId, String conceptCode, UUID programId, Instant dueAt, String reference,
				UUID actingUserId) {
			this(fromPaymentId, conceptCode, programId, dueAt, reference, actingUserId, null, null);
		}
	}

	/**
	 * @param paymentId     the new charge; {@code null} in a preview
	 * @param rateId        the tariff row of the new program
	 * @param newAmount     what the new charge costs: the new tariff minus the
	 *                      discount that travelled
	 * @param alreadyPaid   the money the charge being left kept (paid, less any
	 *                      retroactive saldo it already handed back)
	 * @param otherCredit   saldo a favor the person already had; <b>not</b>
	 *                      applied — they may use it in the portal on the difference
	 * @param creditApplied how much of {@code alreadyPaid} covers the new charge
	 * @param toPay         what is left to pay of the new charge
	 * @param creditLeft    what of {@code alreadyPaid} was left over, as saldo a favor
	 * @param status        the new charge's status ({@code PAID} when covered)
	 * @param tariff        the new program's tariff before the discount
	 */
	record RebillOutcome(UUID paymentId, UUID rateId, BigDecimal newAmount, BigDecimal alreadyPaid,
			BigDecimal otherCredit, BigDecimal creditApplied, BigDecimal toPay, BigDecimal creditLeft,
			PaymentStatus status, BigDecimal tariff) {
	}
}
