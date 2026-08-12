package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The ONLY write use case for {@code PaymentRate} (plan section 2/4). Enforces
 * {@code conceptId} existence via direct {@link PaymentConceptRepository}
 * injection (a new {@link PaymentConceptReferenceNotFoundException}, 400 —
 * {@code PaymentRate} is architecturally a separate aggregate from
 * {@code PaymentConcept}, own repository per plan section 5, so this follows
 * the cross-aggregate-FK convention, not {@code PlanLevel}'s
 * same-aggregate-child reuse of the parent's own 404), {@code programId}
 * existence via {@link AcademicProgramRepository} (reuses the already-existing
 * {@code ProgramNotFoundException}, 400, same one
 * {@code CreateAcademicPlanUseCaseImpl} uses), and {@code periodId} existence
 * via {@link AcademicPeriodRepository} (reuses the already-existing
 * {@code PeriodNotFoundException}, 400, same one {@code CreateGenerationUseCaseImpl}
 * uses) — no new FK-existence exception types, same reuse criterion already
 * applied by {@code Group}.
 *
 * <p>
 * Closing logic (plan section 2): a {@code periodId == null} (continuous)
 * rate closes the currently-active continuous row for the exact same
 * {@code (conceptId, programId, level)} combination before inserting; a
 * {@code periodId != null} (period-scoped) rate closes nothing but is
 * rejected with {@link DuplicatePaymentRateException} (409) if the exact same
 * combination already has a row for that {@code periodId}.
 */
public class SetPaymentRateUseCaseImpl implements SetPaymentRateUseCase {

	private final PaymentRateRepository paymentRateRepository;

	private final PaymentConceptRepository paymentConceptRepository;

	private final AcademicProgramRepository programRepository;

	private final AcademicPeriodRepository periodRepository;

	public SetPaymentRateUseCaseImpl(PaymentRateRepository paymentRateRepository,
			PaymentConceptRepository paymentConceptRepository, AcademicProgramRepository programRepository,
			AcademicPeriodRepository periodRepository) {
		this.paymentRateRepository = paymentRateRepository;
		this.paymentConceptRepository = paymentConceptRepository;
		this.programRepository = programRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public PaymentRateResult setRate(SetPaymentRateCommand command) {
		requireConcept(command.conceptId());
		if (command.programId() != null && programRepository.findById(command.programId()).isEmpty()) {
			throw new ProgramNotFoundException("Academic program not found: " + command.programId());
		}
		if (command.periodId() != null && periodRepository.findById(command.periodId()).isEmpty()) {
			throw new PeriodNotFoundException("Academic period not found: " + command.periodId());
		}
		if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new InvalidPaymentRateDataException("amount must be greater than zero: " + command.amount());
		}

		if (command.periodId() == null) {
			paymentRateRepository
					.findActiveContinuousRate(command.conceptId(), command.programId(), command.level())
					.ifPresent(activeRate -> {
						activeRate.close(command.validFrom());
						paymentRateRepository.save(activeRate);
					});
		} else if (paymentRateRepository.existsByExactCombination(command.conceptId(), command.programId(),
				command.level(), command.periodId())) {
			throw new DuplicatePaymentRateException("A rate already exists for this concept/program/level/period "
					+ "combination: conceptId=" + command.conceptId() + ", programId=" + command.programId()
					+ ", level=" + command.level() + ", periodId=" + command.periodId());
		}

		PaymentRate rate = new PaymentRate(command.conceptId(), command.programId(), command.level(),
				command.amount(), command.periodId(), command.validFrom());
		PaymentRate saved = paymentRateRepository.save(rate);

		return toResult(saved);
	}

	private void requireConcept(UUID conceptId) {
		if (conceptId == null || paymentConceptRepository.findById(conceptId).isEmpty()) {
			throw new PaymentConceptReferenceNotFoundException("Payment concept not found: " + conceptId);
		}
	}

	static PaymentRateResult toResult(PaymentRate rate) {
		return new PaymentRateResult(rate.getId(), rate.getConceptId(), rate.getProgramId(), rate.getLevel(),
				rate.getAmount(), rate.getPeriodId(), rate.getValidFrom(), rate.getValidTo());
	}
}
