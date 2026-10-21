package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Registers the {@code $0} exemption abono (R4, Fase 2 §7.2). The {@code
 * EXEMPT} adeudo already carries the {@code benefitId} and {@code amountDue =
 * $0}; this adds the {@code EXEMPTION} entry with the receipt so the discount
 * has the same "recibo" rastro a real payment has. It is the only write that
 * lands on an {@code EXEMPT} payment, since {@code acceptsEntries()} is false
 * by design.
 */
public class RegisterExemptionUseCaseImpl implements RegisterExemptionUseCase {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final Clock clock;

	public RegisterExemptionUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository,
			Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public CreatePaymentUseCase.PaymentResult registerExemption(RegisterExemptionCommand command) {
		if (command.paymentId() == null) {
			throw new InvalidPaymentDataException("paymentId is required");
		}
		Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));
		if (payment.getStatus() != PaymentStatus.EXEMPT) {
			throw new PaymentNotPayableException(
					"Only EXEMPT payments take an exemption entry, current status: " + payment.getStatus());
		}

		PaymentEntry entry = new PaymentEntry(payment.getId(), ZERO, PaymentEntryMethod.EXEMPTION,
				PaymentChannel.CASHIER, null, null, null, command.reference(), command.receiptNumber(), false,
				command.collectedById(), command.collectedByName(), Instant.now(clock), null);
		paymentEntryRepository.save(entry);

		BenefitSummary benefit = payment.getBenefitId() == null ? null
				: BenefitSummaries.from(paymentBenefitRepository.findById(payment.getBenefitId()).orElse(null));
		return CreatePaymentUseCaseImpl.toResult(payment, benefit);
	}
}
