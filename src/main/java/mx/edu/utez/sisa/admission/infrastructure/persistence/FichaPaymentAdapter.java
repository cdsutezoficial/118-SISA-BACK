package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.port.in.CancelDebtUseCase;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.FreezeDebtQuoteUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetChargeStatusUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.SettleDebtUseCase;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link FichaPaymentPort} over {@code finance}'s generic charge use cases.
 * The seam where admission raises, prices, freezes and settles the ficha's
 * charge; finance types do not leak past this class.
 *
 * <p>Pricing failures are re-worded for the applicant: a concept with no rate
 * for the program is, for her, "the university never configured what this
 * admission costs" — the same {@link FichaPaymentConceptNotFoundException}
 * the rest of the ficha flow already raises.
 */
@Component
public class FichaPaymentAdapter implements FichaPaymentPort {

	/** The online gateway the ficha is charged through (EVO Hosted Checkout). */
	private static final String ONLINE_GATEWAY = "EVO";

	private static final String NOT_PRICED = "La ficha de esta carrera no tiene una tarifa configurada. Contacta a la universidad.";

	private final RaiseDebtUseCase raiseDebtUseCase;

	private final QuoteDebtUseCase quoteDebtUseCase;

	private final FreezeDebtQuoteUseCase freezeDebtQuoteUseCase;

	private final SettleDebtUseCase settleDebtUseCase;

	private final CancelDebtUseCase cancelDebtUseCase;
	private final GetChargeStatusUseCase getChargeStatusUseCase;

	public FichaPaymentAdapter(RaiseDebtUseCase raiseDebtUseCase, QuoteDebtUseCase quoteDebtUseCase,
			FreezeDebtQuoteUseCase freezeDebtQuoteUseCase, SettleDebtUseCase settleDebtUseCase,
			CancelDebtUseCase cancelDebtUseCase, GetChargeStatusUseCase getChargeStatusUseCase) {
		this.getChargeStatusUseCase = getChargeStatusUseCase;
		this.raiseDebtUseCase = raiseDebtUseCase;
		this.quoteDebtUseCase = quoteDebtUseCase;
		this.freezeDebtQuoteUseCase = freezeDebtQuoteUseCase;
		this.settleDebtUseCase = settleDebtUseCase;
		this.cancelDebtUseCase = cancelDebtUseCase;
	}

	@Override
	public UUID raiseFichaDebt(RaiseFichaDebtCommand command) {
		return raiseDebtUseCase.raise(new RaiseDebtUseCase.RaiseDebtCommand(command.candidateId(), null,
				command.conceptCode(), command.programId(), null, null, PaymentOrigin.ADMISSION,
				command.dueAt(), command.referenceNumber()));
	}

	@Override
	public Optional<FichaCharge> findCharge(UUID financePaymentId) {
		return getChargeStatusUseCase.find(financePaymentId).map(FichaPaymentAdapter::toCharge);
	}

	private static FichaCharge toCharge(GetChargeStatusUseCase.ChargeStatus charge) {
		boolean paid = charge.status() == PaymentStatus.PAID || charge.status() == PaymentStatus.EXEMPT;
		return new FichaCharge(charge.paymentId(), charge.referenceNumber(),
				paid ? AdmissionPaymentStatus.PAID : AdmissionPaymentStatus.PENDING,
				charge.status() == PaymentStatus.CANCELLED, charge.dueAt(), charge.createdAt(),
				paid ? charge.paidAt() : null, paid ? charge.receiptNumber() : null);
	}

	@Override
	public FichaQuote quote(UUID financePaymentId) {
		return priced(() -> toQuote(quoteDebtUseCase.quote(financePaymentId, null)));
	}

	@Override
	public FichaQuote freeze(UUID financePaymentId) {
		return priced(() -> toQuote(freezeDebtQuoteUseCase.freeze(financePaymentId, null)));
	}

	@Override
	public SettledFicha settle(SettleFichaCommand command) {
		ChargedAmounts charged = command.charged();
		SettleDebtUseCase.ChargedSnapshot snapshot = charged == null ? null
				: new SettleDebtUseCase.ChargedSnapshot(charged.rateId(), charged.originalAmount(),
						charged.discountAmount(), charged.amountDue(), charged.benefitId());
		SettleDebtUseCase.SettledDebt settled = priced(() -> settleDebtUseCase.settle(
				new SettleDebtUseCase.SettleDebtCommand(command.financePaymentId(), snapshot,
						command.online() ? PaymentEntryMethod.ONLINE : PaymentEntryMethod.CASH,
						command.online() ? PaymentChannel.PORTAL : PaymentChannel.CASHIER,
						command.online() ? ONLINE_GATEWAY : null, command.externalOrderId(), command.receiptNumber(),
						command.reference(), command.collectedById(), command.collectedByName())));
		return new SettledFicha(settled.originalAmount(), settled.discountAmount(), settled.amountDue(),
				settled.amountPaid(), settled.benefitId(), settled.exempt());
	}

	@Override
	public void cancel(UUID financePaymentId) {
		cancelDebtUseCase.cancel(financePaymentId);
	}

	private static <T> T priced(Supplier<T> call) {
		try {
			return call.get();
		} catch (NoRateAvailableException | ConceptNotFoundException ex) {
			throw new FichaPaymentConceptNotFoundException(NOT_PRICED);
		}
	}

	private static FichaQuote toQuote(QuoteDebtUseCase.DebtQuote quote) {
		return new FichaQuote(quote.paymentId(), quote.conceptCode(), quote.conceptName(), quote.rateId(),
				quote.originalAmount(), quote.discountAmount(), quote.amountDue(), quote.benefitId(), quote.exempt(),
				quote.live(), quote.benefit() == null ? null : quote.benefit().reason());
	}
}
