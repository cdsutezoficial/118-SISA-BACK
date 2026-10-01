package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code GET /candidates/{id}} — the ficha projection the applicant's
 * screen refreshes against (the ficha route is only reachable via
 * {@code navigate} state today). Mirrors {@code FichaData} one-to-one.
 *
 * <p>This is the route-refresh fallback for the confirmation screen, which is why
 * it has to carry {@code paymentDeadline} — after a hard refresh the route state
 * is gone, and without it the screen would fall back to a placeholder date
 * instead of the real one. {@code paymentClosesOn} stays for completeness but is
 * the catalog boundary the engine enforces, not the date the screen promises.
 *
 * <p>{@code orderId} is the bank order, and it is nullable on purpose: a ficha that
 * was issued and never paid for has no attempt and therefore no order. The PDF
 * prints it under "Orden de pago (EVO)" only when it exists, and the screen has to
 * make the same distinction — "no has intentado pagar" and "el pago es el
 * 12345" are different facts, not the same field with an empty value.
 */
public record CandidateFichaResponse(UUID candidateId, String folio, CandidateStatus candidateStatus,
		Instant registeredAt, UUID admissionConfigId, String programName, String curp, String firstName,
		String lastName1, String lastName2, String email, String referenceNumber, BigDecimal amount,
		LocalDate registrationDeadline, LocalDate paymentClosesOn, LocalDate paymentDeadline,
		AdmissionPaymentStatus paymentStatus, String receiptNumber, Instant paidAt, String orderId) {

	public static CandidateFichaResponse from(FichaData ficha) {
		return new CandidateFichaResponse(ficha.candidateId(), ficha.folio(), ficha.candidateStatus(),
				ficha.registeredAt(), ficha.admissionConfigId(), ficha.programName(), ficha.curp(),
				ficha.firstName(), ficha.lastName1(), ficha.lastName2(), ficha.email(), ficha.referenceNumber(),
				ficha.amount(), ficha.registrationDeadline(), ficha.paymentClosesOn(), ficha.paymentDeadline(),
				ficha.paymentStatus(), ficha.receiptNumber(), ficha.paidAt(), ficha.orderId());
	}
}