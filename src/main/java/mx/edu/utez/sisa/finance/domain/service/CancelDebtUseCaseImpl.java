package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CancelDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cancels an open, unpaid charge (see {@link CancelDebtUseCase}).
 *
 * <p>A charge that had received part of its price (saldo a favor the person
 * chose to use, and the rest never came) is cancelled too, and what it kept goes
 * back to the person as saldo a favor (D3, D9): money is never left on a charge
 * nobody will pay.
 */
public class CancelDebtUseCaseImpl implements CancelDebtUseCase {

	private final PaymentRepository paymentRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	private final Clock clock;

	public CancelDebtUseCaseImpl(PaymentRepository paymentRepository,
			PaymentCreditRepository paymentCreditRepository, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentCreditRepository = paymentCreditRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public boolean cancel(UUID paymentId) {
		if (paymentId == null) {
			return false;
		}
		Optional<Payment> found = paymentRepository.findByIdForUpdate(paymentId);
		if (found.isEmpty()) {
			return false;
		}
		Payment payment = found.get();
		BigDecimal kept = Payment.moneyKept(payment);
		boolean partlyPaid = (payment.getStatus() == PaymentStatus.PENDING
				|| payment.getStatus() == PaymentStatus.PARTIAL) && kept.signum() > 0;
		if (!payment.isRepriceable() && !partlyPaid) {
			return false;
		}
		payment.cancel();
		paymentRepository.save(payment);
		if (partlyPaid) {
			paymentCreditRepository.save(new PaymentCredit(payment.getCandidateId(), payment.getStudentId(),
					payment.getId(), null, kept, Instant.now(clock)));
		}
		return true;
	}
}
