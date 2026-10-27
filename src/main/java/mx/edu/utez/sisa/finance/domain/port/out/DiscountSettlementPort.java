package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;

import java.time.Instant;
import java.util.List;

/**
 * Fan-out out-port a bounded context implements to react to a newly granted
 * discount <b>on a concept it sells</b> (Fase 2, settle-on-grant).
 *
 * <p>Finance owns the discount; it does not own the adeudos of other contexts.
 * When a discount is created, the module that prices that concept has to decide
 * what it means for its own unpaid/paid charges:
 *
 * <ul>
 * <li>an unpaid charge the discount fully covers is settled right there
 * ({@code admission}: the ficha becomes {@code PAID} at {@code $0} with the
 * benefit as its rastro);</li>
 * <li>a charge the discount partially covers is left pending but will re-price;</li>
 * <li>an already-paid charge turns its overpayment into saldo a favor.</li>
 * </ul>
 *
 * <p>Returns the credits it created (usually none) so finance's own report and
 * tests can see them; finance itself is the source of truth for the discount row
 * regardless. Implementations are looked up as beans and invoked for the concept
 * codes they care about; a module with nothing to do returns an empty list.
 */
public interface DiscountSettlementPort {

	/**
	 * @param benefit the discount just granted
	 * @param now     the grant instant, so every credit the fan-out creates shares
	 *                one timestamp with the discount
	 * @return the saldo-a-favor rows created, oldest charge first; empty when the
	 *         module had nothing to settle or nothing was overpaid
	 */
	List<SettlementResult> onBenefitGranted(PaymentBenefit benefit, Instant now);

	/**
	 * @param creditAmount the overpayment turned into credit ({@code 0} when the
	 *                     charge was merely settled, not refunded)
	 * @param reference    a human-readable reference for logs/report (the charge id)
	 */
	record SettlementResult(java.math.BigDecimal creditAmount, String reference) {
	}
}
