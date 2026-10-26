package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.ListBenefitsUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository.BenefitSearchCriteria;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Lists discounts by candidate/student/concept/status (Fase 2 §5.1), newest
 * first, with the {@code inForce} flag computed against today's business date
 * so the screen can show "vigente/vencido" without re-deriving the window rule.
 */
public class ListBenefitsUseCaseImpl implements ListBenefitsUseCase {

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final Clock clock;

	public ListBenefitsUseCaseImpl(PaymentBenefitRepository paymentBenefitRepository, Clock clock) {
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.clock = clock;
	}

	@Override
	public List<BenefitView> listBenefits(BenefitFilter filter) {
		LocalDate today = LocalDate.now(clock);
		return paymentBenefitRepository
				.search(new BenefitSearchCriteria(filter.candidateId(), filter.studentId(), filter.conceptCode(),
						filter.status(), null, null))
				.stream().map(benefit -> BenefitViews.toView(benefit, today)).toList();
	}
}
