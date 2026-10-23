package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
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

	private final PaymentRepository paymentRepository;

	private final RetroactiveDiscountGranter retroactiveDiscountGranter;

	private final Clock clock;

	public CreateBenefitUseCaseImpl(AnchorQueryPort anchorQueryPort, PaymentBenefitRepository paymentBenefitRepository,
			PaymentRepository paymentRepository, RetroactiveDiscountGranter retroactiveDiscountGranter, Clock clock) {
		this.anchorQueryPort = anchorQueryPort;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.paymentRepository = paymentRepository;
		this.retroactiveDiscountGranter = retroactiveDiscountGranter;
		this.clock = clock;
	}

	@Override
	@Transactional
	public BenefitView createBenefit(CreateBenefitCommand command) {
		if (command.paymentId() != null) {
			return createOnCharge(command);
		}
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

	/**
	 * The staff flow: a discount granted on a specific charge. The anchor and
	 * the concept are the charge's own — whatever the request carried for them
	 * is ignored, so a discount can never land on a concept the charge is not.
	 * A cancelled charge cannot take a discount; a charge already paid can
	 * (it turns into saldo a favor, R12).
	 */
	private BenefitView createOnCharge(CreateBenefitCommand command) {
		Payment charge = paymentRepository.findById(command.paymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));
		if (charge.getStatus() == PaymentStatus.CANCELLED) {
			throw new InvalidBenefitDataException("No se puede aplicar un descuento a un cargo cancelado.");
		}
		if (charge.getStatus() == PaymentStatus.EXEMPT) {
			throw new BenefitNotEditableException("El cargo ya está exento; no admite otro descuento.");
		}
		if (charge.getStatus() == PaymentStatus.PARTIAL) {
			throw new BenefitNotEditableException(
					"El cargo ya tiene abonos; el descuento no se puede aplicar hasta que se liquide.");
		}
		// One discount per charge: a second one is an edit of the first, not an
		// addition. Cancelled ones do not count.
		boolean hasOne = paymentBenefitRepository.findByPaymentId(charge.getId()).stream()
				.anyMatch(existing -> existing.getStatus() != BenefitStatus.CANCELLED);
		if (hasOne) {
			throw new BenefitNotEditableException(
					"Este cargo ya tiene un descuento. Edítalo en lugar de agregar otro.");
		}
		CreateBenefitCommand onCharge = new CreateBenefitCommand(charge.getCandidateId(), charge.getStudentId(),
				charge.getConceptCode(), command.periodId(), command.generationId(), command.mode(), command.percent(),
				command.amount(), command.validFrom(), command.validTo(), command.reason(), command.grantedById(),
				command.grantedByName(), charge.getId());
		validate(onCharge);
		Instant now = Instant.now(clock);
		PaymentBenefit benefit = new PaymentBenefit(charge.getId(), onCharge.candidateId(), onCharge.studentId(),
				onCharge.conceptCode(), onCharge.periodId(), onCharge.generationId(), onCharge.mode(),
				onCharge.percent(), onCharge.amount(), onCharge.validFrom(), onCharge.validTo(), onCharge.reason(),
				onCharge.grantedById(), onCharge.grantedByName(), now);
		PaymentBenefit saved = paymentBenefitRepository.save(benefit);
		retroactiveDiscountGranter.grant(saved, now);
		if (charge.getStatus() == PaymentStatus.PAID) {
			// Granted after the money arrived: it already became saldo a favor
			// (R12), so it is consumed on the spot and, with it, closed to edits.
			saved.markUsed();
			PaymentBenefit used = paymentBenefitRepository.save(saved);
			saved = used != null ? used : saved;
		}
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
