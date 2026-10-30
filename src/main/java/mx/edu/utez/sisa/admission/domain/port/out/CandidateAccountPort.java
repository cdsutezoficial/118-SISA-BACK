package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Los cargos y saldos a favor de un candidato en Finanzas, para el detalle que
 * ve Servicios Escolares. Es solo lectura.
 */
public interface CandidateAccountPort {

	List<Charge> findCharges(UUID candidateId);

	List<Credit> findCredits(UUID candidateId);

	/**
	 * @param status       estado del cargo en Finanzas ({@code PENDING}, {@code PARTIAL}, {@code PAID},
	 *                     {@code EXEMPT}, {@code OVERDUE}, {@code CANCELLED})
	 * @param programName  carrera a la que se cobró; {@code null} si el cargo no tiene carrera
	 */
	record Charge(UUID paymentId, String conceptCode, String referenceNumber, String programName, String status,
			BigDecimal originalAmount, BigDecimal discountAmount, String discountReason, BigDecimal amountDue,
			BigDecimal amountPaid, BigDecimal balance, Instant dueAt, Instant createdAt, List<Entry> entries) {
	}

	/** Un abono al cargo: en línea, en caja o con saldo a favor ({@code CREDIT}). */
	record Entry(BigDecimal amount, String method, String channel, String receiptNumber, String reference,
			Instant paidAt) {
	}

	/** {@code status}: {@code AVAILABLE}, {@code APPLIED} o {@code REFUNDED}. */
	record Credit(BigDecimal amount, String status, UUID originPaymentId, UUID appliedToPaymentId,
			Instant createdAt) {
	}
}
