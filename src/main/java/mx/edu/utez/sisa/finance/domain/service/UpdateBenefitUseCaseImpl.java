package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Edits an {@code ACTIVE}, not-yet-applied discount (Fase 2 §5.1). Two guards
 * run before any change: the discount must be {@code ACTIVE}, and no payment
 * may reference it — an applied discount is immutable, so a settled amount is
 * never silently rewritten.
 */
public class UpdateBenefitUseCaseImpl implements UpdateBenefitUseCase {

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentRepository paymentRepository;

	private final Clock clock;

	public UpdateBenefitUseCaseImpl(PaymentBenefitRepository paymentBenefitRepository,
			PaymentRepository paymentRepository, Clock clock) {
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.paymentRepository = paymentRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public BenefitView updateBenefit(UpdateBenefitCommand command) {
		PaymentBenefit benefit = paymentBenefitRepository.findById(command.id())
				.orElseThrow(() -> new BenefitNotFoundException("Benefit not found: " + command.id()));
		if (!benefit.isActive()) {
			throw new BenefitNotEditableException(benefit.getStatus() == BenefitStatus.USED
					? "El descuento ya se aplicó a un pago; no se puede editar."
					: "Solo un descuento vigente se puede editar.");
		}
		// A discount that settled a charge is USED (and therefore not ACTIVE,
		// refused above). One that only priced an open checkout can still be
		// corrected: the attempt keeps its own snapshot of what it charged.
		if (paymentRepository.isBenefitSettled(benefit.getId())) {
			throw new BenefitNotEditableException("El descuento ya se aplicó a un pago; no se puede editar.");
		}
		// A discount granted on a charge follows that charge: once it is paid (or
		// has abonos) the price is history and the discount with it.
		if (benefit.getPaymentId() != null && paymentRepository.findById(benefit.getPaymentId())
				.map(charge -> !charge.isRepriceable()).orElse(false)) {
			throw new BenefitNotEditableException("El cargo ya está pagado; el descuento no se puede editar.");
		}
		CreateBenefitUseCaseImpl.validateGrant(command.mode(), command.percent(), command.amount());
		BenefitValidation.validateWindow(command.validFrom(), command.validTo());
		if (command.reason() == null || command.reason().isBlank()) {
			throw new InvalidBenefitDataException("reason is required");
		}
		benefit.applyUpdate(command.periodId(), command.generationId(), command.mode(), command.percent(),
				command.amount(), command.validFrom(), command.validTo(), command.reason());
		return BenefitViews.toView(paymentBenefitRepository.save(benefit), LocalDate.now(clock));
	}
}
