package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
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
			throw new BenefitNotEditableException(benefit.getStatus() == BenefitStatus.USED
					? "El descuento ya se aplicó a un pago; no se puede cancelar."
					: "Solo un descuento vigente se puede cancelar.");
		}
		// A discount that settled a charge is USED (and therefore not ACTIVE,
		// refused above). One that only priced an open checkout can still be
		// corrected: the attempt keeps its own snapshot of what it charged.
		if (paymentRepository.isBenefitSettled(benefit.getId())) {
			throw new BenefitNotEditableException("El descuento ya se aplicó a un pago; no se puede cancelar.");
		}
		// A discount granted on a charge follows that charge: once it is paid (or
		// has abonos) the price is history and the discount with it.
		if (benefit.getPaymentId() != null && paymentRepository.findById(benefit.getPaymentId())
				.map(charge -> !charge.isRepriceable()).orElse(false)) {
			throw new BenefitNotEditableException("El cargo ya está pagado; el descuento no se puede cancelar.");
		}
		benefit.cancel();
		return BenefitViews.toView(paymentBenefitRepository.save(benefit), LocalDate.now(clock));
	}
}
