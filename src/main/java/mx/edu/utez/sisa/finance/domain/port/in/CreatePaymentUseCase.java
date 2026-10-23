package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Creates the {@code Payment} adeudo in {@code PENDING} (or {@code EXEMPT} for
 * a FULL benefit) by running the quotation at that moment and freezing it
 * (R1/R3).
 */
public interface CreatePaymentUseCase {

	PaymentResult createPayment(CreatePaymentCommand command);

	/**
	 * @param origin      required — which flow raised the adeudo
	 * @param dueAt       optional — deadline; {@code null} until the config
	 *                    (Fase 1) or a deferral/installment (Fase 3) sets one
	 * @param candidateId aspirante anchor — exactly one of
	 *                    {@code candidateId}/{@code studentId} is required
	 * @param studentId   student anchor
	 */
	record CreatePaymentCommand(UUID candidateId, UUID studentId, String conceptCode, UUID periodId,
			UUID generationId, UUID programId, AcademicLevel level, PaymentOrigin origin, Instant dueAt,
			LocalDate onDate, UUID createdBy) {
	}

	record PaymentResult(UUID id, UUID candidateId, UUID studentId, String conceptCode, BigDecimal originalAmount,
			BigDecimal amountDue, BigDecimal amountPaid, PaymentSnapshotStatus status, PaymentOrigin origin, Instant dueAt,
			Instant createdAt, QuotePaymentUseCase.BenefitSummary benefit) {
	}

	/**
	 * String mirror of {@link mx.edu.utez.sisa.finance.domain.model.PaymentStatus}
	 * for the web layer — same separation every module keeps between the
	 * domain enum and its responses.
	 */
	enum PaymentSnapshotStatus {
		PENDING, PARTIAL, PAID, EXEMPT, OVERDUE, CANCELLED
	}
}
