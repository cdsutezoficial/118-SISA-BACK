package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository.BenefitSearchCriteria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Discount report (Fase 2 §5.1): every discount granted in a date range, newest
 * first. {@code from}/{@code to} bound the grant date ({@code createdAt}); both
 * optional, and the response echoes them so the frontend can title the export.
 */
public class GetDiscountReportUseCaseImpl implements GetDiscountReportUseCase {

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final Clock clock;

	public GetDiscountReportUseCaseImpl(PaymentBenefitRepository paymentBenefitRepository, Clock clock) {
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.clock = clock;
	}

	@Override
	public DiscountReport getReport(Instant from, Instant to) {
		LocalDate today = LocalDate.now(clock);
		List<BenefitView> items = paymentBenefitRepository
				.search(new BenefitSearchCriteria(null, null, null, null, from, to))
				.stream().map(benefit -> BenefitViews.toView(benefit, today)).toList();
		return new DiscountReport(from, to, items.size(), items);
	}
}
