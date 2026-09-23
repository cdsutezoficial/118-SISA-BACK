package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for {@code POST /candidates} 201 responses — the folio the applicant
 * (and Screens 5/6 "Pago de ficha") needs plus the generated ticket payment
 * (reference, amount, deadline), that screens 5/6/13 render. Projection of
 * {@code RegisterCandidateUseCase.CandidateRegistrationResult}, same shape.
 */
public record CandidateRegistrationResponse(UUID id, UUID personId, UUID admissionConfigId, String folio,
		CandidateStatus status, boolean llaveMxVerified, Instant registeredAt, boolean isFirstChoice,
		UUID outreachChannelId, boolean isEnabledForInduction, PaymentResponse payment) {

	/** The admission-ticket payment generated with the registration. */
	public record PaymentResponse(String referenceNumber, BigDecimal amount, LocalDate deadline,
			AdmissionPaymentStatus status) {
	}

	public static CandidateRegistrationResponse from(
			RegisterCandidateUseCase.CandidateRegistrationResult result) {
		RegisterCandidateUseCase.FichaPayment payment = result.payment();
		return new CandidateRegistrationResponse(result.id(), result.personId(), result.admissionConfigId(),
				result.folio(), result.status(), result.llaveMxVerified(), result.registeredAt(),
				result.isFirstChoice(), result.outreachChannelId(), result.isEnabledForInduction(),
				new PaymentResponse(payment.referenceNumber(), payment.amount(), payment.deadline(),
						payment.paymentStatus()));
	}
}