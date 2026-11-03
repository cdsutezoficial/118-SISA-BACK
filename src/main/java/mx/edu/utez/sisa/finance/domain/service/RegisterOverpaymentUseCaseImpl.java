package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterOverpaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Overpayment as saldo a favor (see {@link RegisterOverpaymentUseCase}).
 */
public class RegisterOverpaymentUseCaseImpl implements RegisterOverpaymentUseCase {

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	private final Clock clock;

	public RegisterOverpaymentUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentCreditRepository paymentCreditRepository,
			Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentCreditRepository = paymentCreditRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Optional<Overpayment> register(UUID originPaymentId, String externalOrderId, BigDecimal amount) {
		if (externalOrderId == null || externalOrderId.isBlank() || amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("An overpayment needs its order and a positive amount");
		}
		// The order that paid the charge has its abono: nothing of it is left over.
		if (paymentEntryRepository.existsByExternalOrderId(externalOrderId)) {
			return Optional.empty();
		}
		Optional<PaymentCredit> booked = paymentCreditRepository.findByExternalOrderId(externalOrderId);
		if (booked.isPresent()) {
			return Optional.of(new Overpayment(booked.get().getId(), booked.get().getAmount(), false));
		}
		Payment origin = paymentRepository.findById(originPaymentId)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + originPaymentId));
		PaymentCredit credit = paymentCreditRepository.save(PaymentCredit.overpayment(origin.getCandidateId(),
				origin.getStudentId(), origin.getId(), externalOrderId, amount, Instant.now(clock)));
		return Optional.of(new Overpayment(credit.getId(), credit.getAmount(), true));
	}
}
