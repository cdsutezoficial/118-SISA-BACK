package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase.PaymentAccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response of {@code POST /candidates/payment-access} — deliberately the payment
 * view of the ficha and nothing more.
 *
 * <p>It does NOT carry address, phone, health profile, diversity profile,
 * income or grade point average: those stay behind the UUID-only
 * {@code GET /candidates/{id}}. This endpoint is reachable with a sequential
 * folio plus 3 CURP characters, so widening it would turn a weak identity proof
 * into a personal-data leak.
 *
 * <p>{@code alreadyPaid} lets the payment screen show the receipt without
 * calling EVO again — the applicant who already paid sees her confirmation
 * instead of a checkout button that would 409. {@code paidAt} carries the
 * confirmation date the business asked to display next to the receipt folio.
 */
public record FichaPaymentAccessResponse(UUID candidateId, String folio, String nombre, String programName,
		BigDecimal amount, String referenceNumber, LocalDate deadline, AdmissionPaymentStatus paymentStatus,
		String receiptNumber, Instant paidAt, boolean alreadyPaid) {

	public static FichaPaymentAccessResponse from(PaymentAccess access) {
		return new FichaPaymentAccessResponse(access.candidateId(), access.folio(), access.nombre(),
				access.programName(), access.amount(), access.referenceNumber(), access.deadline(),
				access.paymentStatus(), access.receiptNumber(), access.paidAt(), access.alreadyPaid());
	}
}
