package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.FreezeDebtQuoteUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Freezes the live quote onto the charge (see {@link FreezeDebtQuoteUseCase}).
 */
public class FreezeDebtQuoteUseCaseImpl implements FreezeDebtQuoteUseCase {

	private final PaymentRepository paymentRepository;

	private final DebtQuoter debtQuoter;

	private final Clock clock;

	public FreezeDebtQuoteUseCaseImpl(PaymentRepository paymentRepository, DebtQuoter debtQuoter, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.debtQuoter = debtQuoter;
		this.clock = clock;
	}

	@Override
	@Transactional
	public DebtQuote freeze(UUID paymentId, LocalDate onDate) {
		if (paymentId == null) {
			throw new InvalidPaymentDataException("paymentId is required");
		}
		Payment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
		return freeze(payment, onDate == null ? LocalDate.now(clock) : onDate);
	}

	/**
	 * Same freeze, on a charge the caller already holds under lock (the settle
	 * flow), so the two use the exact same rule.
	 */
	public DebtQuote freeze(Payment payment, LocalDate onDate) {
		if (!payment.isRepriceable()) {
			throw new PaymentNotPayableException(
					"La cuenta " + payment.getId() + " ya no se puede cotizar (estado " + payment.getStatus() + ").");
		}
		DebtQuote quote = debtQuoter.live(payment, onDate);
		// Stays PENDING even at $0: EXEMPT means the exemption was booked, which
		// is the settle step's job (with its receipt), not the quote's.
		payment.reprice(quote.originalAmount(), quote.amountDue(), quote.benefitId(), quote.rateId(),
				PaymentStatus.PENDING);
		paymentRepository.save(payment);
		return new DebtQuote(quote.paymentId(), quote.candidateId(), quote.studentId(), quote.conceptCode(),
				quote.conceptName(), payment.getStatus(), quote.rateId(), quote.originalAmount(),
				quote.discountAmount(), quote.amountDue(), quote.amountPaid(), quote.benefit(), quote.exempt(), false,
				quote.dueAt(), null);
	}
}
