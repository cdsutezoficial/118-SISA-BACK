package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Applies the discount in force for a {@code (candidateId|studentId,
 * conceptCode)} to an amount the caller already knows (Fase 2, cross-context).
 * Reuses the same
 * {@link BenefitResolver} and {@link PaymentQuoter} the finance quoter uses, so
 * a discount quoted by {@code admission} for a ficha is priced byte-for-byte
 * like any other concept.
 *
 * <p>No concept/rate lookup and no persistence: the rate is the caller's. The
 * three rules that matter — window/anchor matching, mode arithmetic, and MXN
 * rounding — are delegated, never reimplemented, which is the whole point of
 * exposing this instead of letting each module read {@code payment_benefit}.
 */
public class QuoteBenefitUseCaseImpl implements QuoteBenefitUseCase {

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final BenefitResolver benefitResolver;

	private final PaymentQuoter paymentQuoter;

	private final Clock clock;

	public QuoteBenefitUseCaseImpl(PaymentBenefitRepository paymentBenefitRepository, BenefitResolver benefitResolver,
			PaymentQuoter paymentQuoter, Clock clock) {
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.benefitResolver = benefitResolver;
		this.paymentQuoter = paymentQuoter;
		this.clock = clock;
	}

	@Override
	public BenefitQuote quote(BenefitQuoteCommand command) {
		LocalDate onDate = command.onDate() == null ? LocalDate.now(clock) : command.onDate();
		Optional<PaymentBenefit> benefit = benefitResolver.resolve(
				command.candidateId() != null
						? paymentBenefitRepository.findActiveByCandidateAndConcept(command.candidateId(),
								command.conceptCode())
						: paymentBenefitRepository.findActiveByStudentAndConcept(command.studentId(),
								command.conceptCode()),
				onDate, command.periodId(), command.generationId());
		PaymentQuoter.Quotation quotation = paymentQuoter.quote(command.rateAmount(), benefit.orElse(null));
		PaymentBenefit applied = quotation.benefit();
		return new BenefitQuote(quotation.original(), quotation.discount(), quotation.due(),
				applied == null ? null : applied.getId(), applied == null ? null : applied.getMode(),
				quotation.exempt());
	}
}
