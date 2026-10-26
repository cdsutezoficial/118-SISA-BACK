package mx.edu.utez.sisa.finance.domain.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Discount report (Fase 2 §5.1): every discount granted in a date range, so
 * the frontend can render and export it. {@code from}/{@code to} bound the
 * grant ({@code createdAt}) date; both optional ({@code null} = open side).
 */
public interface GetDiscountReportUseCase {

	DiscountReport getReport(Instant from, Instant to);

	record DiscountReport(Instant from, Instant to, long count, List<BenefitView> items) {
	}
}
