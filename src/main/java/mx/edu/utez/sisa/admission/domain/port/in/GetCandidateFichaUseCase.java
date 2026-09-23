package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read query for the admission ficha: the applicant's registration plus her
 * ticket payment and program name. Assembles data owned by different
 * repositories ({@code Candidate}, its {@code Person}, the
 * {@code ADMISSION_FICHA} {@code AdmissionPayment}, and the program name from
 * {@code academic_config}). Governs {@code GET /candidates/{id}}, the ficha
 * PDF and the content of the payment emails — one query the web layer reuses
 * for all three consumers. Missing candidate or payment → empty by design;
 * the web layer maps to HTTP 404.
 */
public interface GetCandidateFichaUseCase {

	FichaData get(UUID candidateId);

	record FichaData(UUID candidateId, String folio, CandidateStatus candidateStatus, Instant registeredAt,
			UUID admissionConfigId, String programName, String curp, String firstName, String lastName1,
			String lastName2, String email, String referenceNumber, BigDecimal amount, LocalDate deadline,
			AdmissionPaymentStatus paymentStatus, String receiptNumber, Instant paidAt) {
	}
}