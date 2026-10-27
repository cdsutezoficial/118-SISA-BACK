package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.port.in.CancelDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Cancels an open, unpaid charge (see {@link CancelDebtUseCase}).
 */
public class CancelDebtUseCaseImpl implements CancelDebtUseCase {

	private final PaymentRepository paymentRepository;

	public CancelDebtUseCaseImpl(PaymentRepository paymentRepository) {
		this.paymentRepository = paymentRepository;
	}

	@Override
	@Transactional
	public boolean cancel(UUID paymentId) {
		if (paymentId == null) {
			return false;
		}
		Optional<Payment> found = paymentRepository.findByIdForUpdate(paymentId);
		if (found.isEmpty() || !found.get().isRepriceable()) {
			return false;
		}
		Payment payment = found.get();
		payment.cancel();
		paymentRepository.save(payment);
		return true;
	}
}
