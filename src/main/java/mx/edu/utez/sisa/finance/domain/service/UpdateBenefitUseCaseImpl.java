package mx.edu.utez.sisa.finance.domain.service;

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
			throw new BenefitNotEditableException(
					"Only ACTIVE benefits can be edited, current status: " + benefit.getStatus());
		}
		if (paymentRepository.existsByBenefitId(benefit.getId())) {
			throw new BenefitNotEditableException("A benefit already applied to a payment cannot be edited");
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
