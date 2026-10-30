package mx.edu.utez.sisa.admission.domain.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pagos del candidato para el detalle de Servicios Escolares: todos sus cobros
 * (la ficha vigente, las diferencias por cambio de carrera y los cobros que
 * quedaron como historial), su saldo a favor y sus cambios de carrera.
 */
public interface GetCandidateAccountUseCase {

	CandidateAccount get(UUID candidateId);

	/** @param availableCredit saldo a favor que puede usar hoy */
	record CandidateAccount(UUID candidateId, BigDecimal availableCredit, List<ChargeView> charges,
			List<CreditView> credits, List<ProgramChangeView> programChanges) {
	}

	/** Para qué sirve el cobro hoy. */
	enum ChargeRole {
		/** La ficha que cuenta (pagada o por pagar). */
		CURRENT_FICHA,
		/** La diferencia de un cambio de carrera que falta pagar. */
		PENDING_DIFFERENCE,
		/** Un cobro anterior: quedó pagado y lo reemplazó un cambio de carrera, o se canceló. */
		HISTORY,
		/** Otro concepto (no la ficha). */
		OTHER
	}

	record ChargeView(UUID paymentId, ChargeRole role, String conceptCode, String referenceNumber,
			String programName, String status, BigDecimal originalAmount, BigDecimal discountAmount,
			String discountReason, BigDecimal amountDue, BigDecimal amountPaid, BigDecimal balance, Instant dueAt,
			Instant createdAt, List<EntryView> entries) {
	}

	record EntryView(BigDecimal amount, String method, String channel, String receiptNumber, String reference,
			Instant paidAt) {
	}

	/**
	 * @param originReference    referencia del cobro que lo generó
	 * @param appliedToReference referencia del cobro donde se usó; {@code null} si sigue disponible
	 */
	record CreditView(BigDecimal amount, String status, String originReference, String appliedToReference,
			Instant createdAt) {
	}

	record ProgramChangeView(String fromProgramName, String toProgramName, boolean wasPaid,
			BigDecimal previousAmount, BigDecimal paidAmount, BigDecimal newAmount, BigDecimal toPay,
			BigDecimal creditLeft, Instant changedAt) {
	}
}
