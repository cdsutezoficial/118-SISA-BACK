package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a screen shows as the ficha's price: "cuesta X − descuento Y = Z".
 *
 * <p>Read-only. It is the finance charge's quote: live while the charge is
 * open — the same rule the checkout will freeze a moment later, so the screen
 * never promises a number the system will not charge — and the frozen amount
 * once it is paid. Without a charge it falls back to the program's list
 * price. A ficha that cannot be priced shows no amount instead of a made-up one.
 */
public class FichaDisplayPrice {

	private final FichaPaymentPort fichaPaymentPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final Clock clock;

	public FichaDisplayPrice(FichaPaymentPort fichaPaymentPort, FichaAmountResolver fichaAmountResolver,
			Clock clock) {
		this.fichaPaymentPort = fichaPaymentPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.clock = clock;
	}

	/**
	 * @param programId the ficha's program, or {@code null} when unknown
	 */
	public Price of(UUID financePaymentId, UUID programId) {
		try {
			if (financePaymentId != null) {
				FichaPaymentPort.FichaQuote quote = fichaPaymentPort.quote(financePaymentId);
				return new Price(quote.originalAmount(), quote.discountAmount(), quote.amountDue(),
						quote.discountReason());
			}
			if (programId == null) {
				return Price.NONE;
			}
			return new Price(null, null, fichaAmountResolver.resolve(programId, LocalDate.now(clock)).amount(), null);
		} catch (FichaPaymentConceptNotFoundException | PaymentConceptExpiredException
				| AmbiguousFichaPaymentConceptException ex) {
			return Price.NONE;
		}
	}

	/**
	 * @param originalAmount the tariff, or {@code null} when unknown
	 * @param discountAmount the discount, or {@code null} when unknown
	 * @param amount         what is owed, or {@code null} when it cannot be priced
	 * @param discountReason why the discount was granted, or {@code null}
	 */
	public record Price(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amount,
			String discountReason) {

		static final Price NONE = new Price(null, null, null, null);

	}
}
