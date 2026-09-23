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
 */
public record CandidateFichaResponse(UUID candidateId, String folio, CandidateStatus candidateStatus,
		Instant registeredAt, UUID admissionConfigId, String programName, String curp, String firstName,
		String lastName1, String lastName2, String email, String referenceNumber, BigDecimal amount,
		LocalDate deadline, AdmissionPaymentStatus paymentStatus, String receiptNumber, Instant paidAt) {

	public static CandidateFichaResponse from(FichaData ficha) {
		return new CandidateFichaResponse(ficha.candidateId(), ficha.folio(), ficha.candidateStatus(),
				ficha.registeredAt(), ficha.admissionConfigId(), ficha.programName(), ficha.curp(),
				ficha.firstName(), ficha.lastName1(), ficha.lastName2(), ficha.email(), ficha.referenceNumber(),
				ficha.amount(), ficha.deadline(), ficha.paymentStatus(), ficha.receiptNumber(), ficha.paidAt());
	}
}