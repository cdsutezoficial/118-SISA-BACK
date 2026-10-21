package mx.edu.utez.sisa.finance.infrastructure.web;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase.CreditApplicationResult;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.AccountStatement;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.StatementEntry;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.StatementPayment;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase.DiscountReport;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.AccountStatementEntryResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.AccountStatementPaymentResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.AccountStatementResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.BenefitDetailResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.BenefitResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreditApplicationResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.CreditResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.DiscountReportResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.PaymentListItemResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.PaymentResponse;

import java.util.List;

/**
 * Maps domain results to web DTOs for {@code finance}, so the controllers stay
 * thin and the two endpoints that render a payment (create/register and the
 * statement) share one projection.
 */
final class FinanceResponseMapper {

	private FinanceResponseMapper() {
	}

	static BenefitResponse toBenefit(BenefitSummary summary) {
		if (summary == null) {
			return null;
		}
		return new BenefitResponse(summary.id(), summary.mode(), summary.percent(), summary.amount(), summary.reason(),
				summary.grantedByName());
	}

	static PaymentResponse toPayment(PaymentResult result) {
		return new PaymentResponse(result.id(), result.candidateId(), result.studentId(), result.conceptCode(),
				result.originalAmount(), result.amountDue(), result.amountPaid(), result.status(), result.origin().name(),
				result.dueAt(), result.createdAt(), toBenefit(result.benefit()));
	}

	static PaymentListItemResponse toListItem(Payment payment) {
		return new PaymentListItemResponse(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
				payment.getConceptCode(), payment.getOriginalAmount(), payment.getAmountDue(), payment.getAmountPaid(),
				payment.getStatus().name(), payment.getOrigin().name(), payment.getDueAt(), payment.getCreatedAt());
	}

	static AccountStatementResponse toStatement(AccountStatement statement) {
		List<AccountStatementPaymentResponse> payments = statement.payments().stream()
				.map(FinanceResponseMapper::toStatementPayment)
				.toList();
		return new AccountStatementResponse(statement.candidateId(), statement.studentId(), statement.personName(),
				payments);
	}

	static AccountStatementPaymentResponse toStatementPayment(StatementPayment payment) {
		List<AccountStatementEntryResponse> entries = payment.entries().stream()
				.map(FinanceResponseMapper::toStatementEntry)
				.toList();
		return new AccountStatementPaymentResponse(payment.id(), payment.conceptCode(), payment.originalAmount(),
				payment.amountDue(), payment.amountPaid(), payment.balance(), payment.status(), payment.origin().name(),
				payment.dueAt(), payment.createdAt(), toBenefit(payment.benefit()), entries);
	}

	static AccountStatementEntryResponse toStatementEntry(StatementEntry entry) {
		return new AccountStatementEntryResponse(entry.id(), entry.amount(), entry.method(), entry.channel(),
				entry.receiptNumber(), entry.reference(), entry.isPhysical(), entry.collectedByName(), entry.paidAt());
	}

	static BenefitDetailResponse toBenefitDetail(BenefitView view) {
		return new BenefitDetailResponse(view.id(), view.candidateId(), view.studentId(), view.conceptCode(),
				view.periodId(), view.generationId(), view.mode(), view.percent(), view.amount(), view.validFrom(),
				view.validTo(), view.status(), view.reason(), view.grantedById(), view.grantedByName(), view.createdAt(),
				view.inForce());
	}

	static CreditResponse toCredit(PaymentCredit credit) {
		return new CreditResponse(credit.getId(), credit.getCandidateId(), credit.getStudentId(),
				credit.getOriginPaymentId(), credit.getBenefitId(), credit.getAmount(), credit.getStatus(),
				credit.getAppliedToPaymentId(), credit.getCreatedAt());
	}

	static DiscountReportResponse toReport(DiscountReport report) {
		List<BenefitDetailResponse> items = report.items().stream().map(FinanceResponseMapper::toBenefitDetail)
				.toList();
		return new DiscountReportResponse(report.from(), report.to(), report.count(), items);
	}

	static CreditApplicationResponse toCreditApplication(CreditApplicationResult result) {
		return new CreditApplicationResponse(toPayment(result.payment()), result.creditApplied(),
				result.appliedCreditIds());
	}
}
