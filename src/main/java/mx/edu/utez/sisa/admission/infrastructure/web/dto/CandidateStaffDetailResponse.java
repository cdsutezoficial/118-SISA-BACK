package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Staff-only detail projection behind {@code GET /candidates/{id}/detail}.
 * Reuses the assembled ficha query but narrows it to the fields the office's
 * detail screen actually shows on its first two tabs.
 */
public record CandidateStaffDetailResponse(UUID candidateId, String folio, String fullName,
		CandidateStatus candidateStatus, Instant registeredAt, String programName, String divisionName, String curp,
		String email, String homePhone, String mobilePhone, String outreachChannelName,
		PaymentResponse admissionPayment, PaymentResponse inductionPayment) {

	public record PaymentResponse(AdmissionPaymentConcept concept, BigDecimal amount, String referenceNumber,
			AdmissionPaymentStatus paymentStatus, String receiptNumber, Instant paidAt, String orderId) {

		public static PaymentResponse from(FichaData.PaymentData payment) {
			return payment == null ? null
					: new PaymentResponse(payment.concept(), payment.amount(), payment.referenceNumber(),
							payment.paymentStatus(), payment.receiptNumber(), payment.paidAt(), payment.orderId());
		}
	}

	public static CandidateStaffDetailResponse from(FichaData ficha) {
		return new CandidateStaffDetailResponse(ficha.candidateId(), ficha.folio(), fullName(ficha),
				ficha.candidateStatus(), ficha.registeredAt(), ficha.programName(), ficha.divisionName(), ficha.curp(),
				ficha.email(), ficha.homePhone(), ficha.mobilePhone(),
				ficha.seleccionCarrera() == null ? null : ficha.seleccionCarrera().outreachChannelName(),
				new PaymentResponse(AdmissionPaymentConcept.ADMISSION_FICHA, ficha.amount(), ficha.referenceNumber(),
						ficha.paymentStatus(), ficha.receiptNumber(), ficha.paidAt(), ficha.orderId()),
				PaymentResponse.from(ficha.inductionPayment()));
	}

	private static String fullName(FichaData ficha) {
		return String.join(" ",
				java.util.stream.Stream.of(ficha.firstName(), ficha.lastName1(), ficha.lastName2())
						.filter(part -> part != null && !part.isBlank())
						.toList());
	}
}