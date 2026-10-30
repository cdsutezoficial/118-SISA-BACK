package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado de cuenta: every adeudo of a candidato/estudiante with its abonos and
 * the applied discount — the read model behind the statement screen and the
 * reports.
 */
public interface GetAccountStatementUseCase {

	AccountStatement getAccountStatement(UUID candidateId, UUID studentId);

	record AccountStatement(UUID candidateId, UUID studentId, String personName, List<StatementPayment> payments) {
	}

	/**
	 * One adeudo with its full detail. {@code benefit} is present only when a
	 * discount was frozen into the payment; {@code entries} may be empty.
	 */
	record StatementPayment(UUID id, String conceptCode, BigDecimal originalAmount, BigDecimal amountDue,
			BigDecimal amountPaid, BigDecimal balance, PaymentStatus status, PaymentOrigin origin, Instant dueAt,
			Instant createdAt, QuotePaymentUseCase.BenefitSummary benefit, List<StatementEntry> entries,
			String referenceNumber, UUID programId) {
	}

	record StatementEntry(UUID id, BigDecimal amount, PaymentEntryMethod method, PaymentChannel channel,
			String receiptNumber, String reference, boolean isPhysical, String collectedByName, Instant paidAt) {
	}
}
