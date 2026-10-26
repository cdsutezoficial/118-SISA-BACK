package mx.edu.utez.sisa.finance.domain.port.in;

import java.time.Instant;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Prices an existing charge (adeudo) <em>live</em>, without writing anything.
 *
 * <p>A charge is raised as a header: who owes, which catalog concept, which
 * program. Its price is never stored while it is open; every time someone looks
 * at it (the payer on the portal, staff on the debts list, the ficha PDF) it is
 * computed from the concept's rate in force today and the discount in force
 * today. Only when the payer acts (checkout, caja) are the numbers frozen, by
 * {@link FreezeDebtQuoteUseCase}.
 *
 * <p>A charge that is no longer open ({@code PAID}, {@code PARTIAL},
 * {@code CANCELLED}, …) is reported with the numbers it was frozen with:
 * re-pricing history would change what was agreed.
 */
public interface QuoteDebtUseCase {

	/**
	 * @param paymentId the charge
	 * @param onDate    the business date the rate and discount are evaluated on;
	 *                  {@code null} means today
	 * @throws mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException
	 *         if the charge does not exist
	 * @throws mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException
	 *         if the concept has no rate for the charge's program
	 */
	DebtQuote quote(UUID paymentId, LocalDate onDate);

	/**
	 * Every charge of one person (exactly one of the two anchors), each priced
	 * live. A charge that cannot be priced is still listed, with
	 * {@link DebtQuote#pricingError()} set and no amounts, so staff can see it
	 * and fix the catalog.
	 */
	List<DebtQuote> quoteAll(UUID candidateId, UUID studentId);

	/**
	 * The "Aplicar descuento" view of a person's charges: each one priced live
	 * (as {@link #quoteAll}), plus <b>the</b> discount granted on it — there is
	 * at most one per charge, so the screen edits it instead of adding another —
	 * and the saldo a favor it produced when the discount came after payment.
	 */
	List<ChargeView> listCharges(UUID candidateId, UUID studentId);

	/**
	 * @param quote        the charge priced live (or frozen, when closed)
	 * @param discount     the discount granted on this charge, or {@code null}
	 * @param editable     whether that discount can still be edited: it is
	 *                     in force and the charge has not been paid
	 * @param creditAmount saldo a favor this charge generated ({@code 0} when none)
	 */
	record ChargeView(DebtQuote quote, BenefitView discount, boolean editable, BigDecimal creditAmount) {
	}

	/**
	 * @param paymentId      the charge
	 * @param conceptCode    the catalog concept the charge is for
	 * @param conceptName    the concept's name, for display
	 * @param status         the charge's status
	 * @param rateId         the {@code payment_rate} row that priced it
	 * @param originalAmount the tariff before the discount
	 * @param discountAmount the discount ({@code 0} when none)
	 * @param amountDue      what the payer owes ({@code 0} when exempt)
	 * @param amountPaid     what has been paid against the charge so far
	 * @param benefit        the discount applied, or {@code null}
	 * @param exempt         {@code true} when a discount covers the whole tariff
	 * @param live           {@code true} when computed now; {@code false} when
	 *                       these are the numbers frozen on a closed charge
	 * @param dueAt          the charge's deadline, when it has one
	 * @param pricingError   why the charge could not be priced, or {@code null}
	 */
	record DebtQuote(UUID paymentId, UUID candidateId, UUID studentId, String conceptCode, String conceptName,
			PaymentStatus status, UUID rateId, BigDecimal originalAmount, BigDecimal discountAmount,
			BigDecimal amountDue, BigDecimal amountPaid, BenefitSummary benefit, boolean exempt, boolean live,
			Instant dueAt, String pricingError) {

		public UUID benefitId() {
			return benefit == null ? null : benefit.id();
		}
	}
}
