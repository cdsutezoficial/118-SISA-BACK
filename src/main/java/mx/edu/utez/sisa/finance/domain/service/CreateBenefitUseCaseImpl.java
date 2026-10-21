package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Creates a discount and, when the candidato/estudiante has already paid that
 * concept, turns the overpayment into saldo a favor (R12). Runs in one
 * transaction so the discount and the credits it mints either both exist or
 * neither does.
 */
public class CreateBenefitUseCaseImpl implements CreateBenefitUseCase {

	private final AnchorQueryPort anchorQueryPort;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final RetroactiveDiscountGranter retroactiveDiscountGranter;

	private final Clock clock;

	public CreateBenefitUseCaseImpl(AnchorQueryPort anchorQueryPort, PaymentBenefitRepository paymentBenefitRepository,
			RetroactiveDiscountGranter retroactiveDiscountGranter, Clock clock) {
		this.anchorQueryPort = anchorQueryPort;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.retroactiveDiscountGranter = retroactiveDiscountGranter;
		this.clock = clock;
	}

	@Override
	@Transactional
	public BenefitView createBenefit(CreateBenefitCommand command) {
		validate(command);
		boolean candidate = command.candidateId() != null;
		if (candidate ? !anchorQueryPort.candidateExists(command.candidateId())
				: !anchorQueryPort.studentExists(command.studentId())) {
			throw new PersonNotFoundException("Anchor not found: "
					+ (candidate ? command.candidateId() : command.studentId()));
		}
		Instant now = Instant.now(clock);
		PaymentBenefit benefit = new PaymentBenefit(command.candidateId(), command.studentId(), command.conceptCode(),
				command.periodId(), command.generationId(), command.mode(), command.percent(), command.amount(),
				command.validFrom(), command.validTo(), command.reason(), command.grantedById(), command.grantedByName(),
				now);
		PaymentBenefit saved = paymentBenefitRepository.save(benefit);
		retroactiveDiscountGranter.grant(saved, now);
		return BenefitViews.toView(saved, LocalDate.now(clock));
	}

	static void validate(CreateBenefitCommand command) {
		if ((command.candidateId() == null) == (command.studentId() == null)) {
			throw new InvalidBenefitDataException("exactly one of candidateId/studentId is required");
		}
		if (command.conceptCode() == null || command.conceptCode().isBlank()) {
			throw new InvalidBenefitDataException("conceptCode is required");
		}
		validateGrant(command.mode(), command.percent(), command.amount());
		BenefitValidation.validateWindow(command.validFrom(), command.validTo());
		if (command.reason() == null || command.reason().isBlank()) {
			throw new InvalidBenefitDataException("reason is required");
		}
		if (command.grantedById() == null) {
			throw new InvalidBenefitDataException("grantedById is required");
		}
		if (command.grantedByName() == null || command.grantedByName().isBlank()) {
			throw new InvalidBenefitDataException("grantedByName is required");
		}
	}

	static void validateGrant(BenefitMode mode, BigDecimal percent, BigDecimal amount) {
		if (mode == null) {
			throw new InvalidBenefitDataException("mode is required");
		}
		switch (mode) {
			case PERCENT -> {
				if (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
					throw new InvalidBenefitDataException("percent must be in (0, 100] for mode PERCENT");
				}
				if (amount != null) {
					throw new InvalidBenefitDataException("percent mode must not carry an amount");
				}
			}
			case AMOUNT -> {
				if (amount == null || amount.signum() <= 0) {
					throw new InvalidBenefitDataException("amount must be positive for mode AMOUNT");
				}
				if (percent != null) {
					throw new InvalidBenefitDataException("amount mode must not carry a percent");
				}
			}
			case FULL -> {
				if (percent != null || amount != null) {
					throw new InvalidBenefitDataException("mode FULL carries neither percent nor amount");
				}
			}
		}
	}
}
