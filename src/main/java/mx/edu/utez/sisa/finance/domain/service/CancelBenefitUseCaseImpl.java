package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CancelBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cancels a discount that has not been applied (Fase 2 §5.1). Same guards as
 * the edit: {@code ACTIVE} only and never one already referenced by a payment.
 * The row is kept as {@code CANCELLED} for audit — never deleted.
 */
public class CancelBenefitUseCaseImpl implements CancelBenefitUseCase {

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentRepository paymentRepository;

	private final Clock clock;

	public CancelBenefitUseCaseImpl(PaymentBenefitRepository paymentBenefitRepository,
			PaymentRepository paymentRepository, Clock clock) {
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.paymentRepository = paymentRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public BenefitView cancelBenefit(UUID id) {
		PaymentBenefit benefit = paymentBenefitRepository.findById(id)
				.orElseThrow(() -> new BenefitNotFoundException("Benefit not found: " + id));
		if (!benefit.isActive()) {
			throw new BenefitNotEditableException(
					"Only ACTIVE benefits can be cancelled, current status: " + benefit.getStatus());
		}
		if (paymentRepository.existsByBenefitId(benefit.getId())) {
			throw new BenefitNotEditableException("A benefit already applied to a payment cannot be cancelled");
		}
		benefit.cancel();
		return BenefitViews.toView(paymentBenefitRepository.save(benefit), LocalDate.now(clock));
	}
}
