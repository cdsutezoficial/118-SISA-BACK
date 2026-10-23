package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidEntryAmountException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Registers an abono and runs the settlement decider (Fase 1 §5.3, R7/R8).
 *
 * <p>Runs in one transaction that first takes a pessimistic write lock on the
 * adeudo ({@code findByIdForUpdate}) and then recomputes the abono total from
 * the persisted entries — never from the cached {@code amountPaid}. That is
 * what makes two concurrent abonos serialize instead of both paying against
 * the same starting balance (Fase 1 §8).
 *
 * <p>An abono above the remaining balance is rejected, not diverted to a
 * credit: {@code PaymentCredit} (saldo a favor) is Fase 2. A payment already
 * {@code PAID}/{@code EXEMPT}/{@code CANCELLED} does not accept abonos at all
 * ({@link PaymentStatus#acceptsEntries()}).
 */
public class RegisterPaymentEntryUseCaseImpl implements RegisterPaymentEntryUseCase {

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentSettlementDecider settlementDecider;

	private final Clock clock;

	public RegisterPaymentEntryUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository,
			PaymentSettlementDecider settlementDecider, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.settlementDecider = settlementDecider;
		this.clock = clock;
	}

	@Override
	@Transactional
	public CreatePaymentUseCase.PaymentResult registerEntry(RegisterEntryCommand command) {
		validate(command);
		Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));
		if (!payment.getStatus().acceptsEntries()) {
			throw new PaymentNotPayableException(
					"Payment does not accept abonos in status " + payment.getStatus() + ": " + payment.getId());
		}
		BigDecimal amountDue = payment.getAmountDue();
		if (amountDue == null) {
			throw new PaymentNotPayableException(
					"Payment has no quoted amount yet, start checkout before registering abonos: " + payment.getId());
		}
		BigDecimal paidBefore = paymentEntryRepository.sumAmountByPaymentId(payment.getId());
		BigDecimal paidAfter = paidBefore.add(command.amount());
		if (paidAfter.compareTo(amountDue) > 0) {
			throw new PaymentNotPayableException("Abono exceeds the remaining balance of payment: " + payment.getId());
		}

		PaymentEntry entry = new PaymentEntry(payment.getId(), command.amount(), command.method(), command.channel(),
				command.gateway(), command.externalOrderId(), command.linkId(), command.reference(),
				command.receiptNumber(), command.isPhysical(), command.collectedById(), command.collectedByName(),
				Instant.now(clock), command.gatewayStatus());
		paymentEntryRepository.save(entry);

		PaymentStatus newStatus = settlementDecider.decide(amountDue, paidAfter);
		payment.settle(paidAfter, newStatus);
		Payment saved = paymentRepository.save(payment);

		// A discount that priced a charge now fully paid has been consumed: it
		// becomes USED and, with it, immutable (no edit, no cancel).
		BenefitSummary benefit = payment.getBenefitId() == null ? null
				: BenefitSummaries.from(newStatus == PaymentStatus.PAID
						? BenefitUsage.markUsed(paymentBenefitRepository, payment.getBenefitId())
						: paymentBenefitRepository.findById(payment.getBenefitId()).orElse(null));
		return CreatePaymentUseCaseImpl.toResult(saved, benefit);
	}

	static void validate(RegisterEntryCommand command) {
		if (command.paymentId() == null) {
			throw new InvalidEntryAmountException("paymentId is required");
		}
		if (command.amount() == null || command.amount().signum() <= 0) {
			throw new InvalidEntryAmountException("amount must be positive");
		}
		if (command.method() == null) {
			throw new InvalidEntryAmountException("method is required");
		}
		if (command.channel() == null) {
			throw new InvalidEntryAmountException("channel is required");
		}
	}
}
