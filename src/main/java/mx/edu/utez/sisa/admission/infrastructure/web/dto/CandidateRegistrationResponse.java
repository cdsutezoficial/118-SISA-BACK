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
 * (reference, amount and the two window dates), that screens 5/6/13 render.
 * Projection of {@code RegisterCandidateUseCase.CandidateRegistrationResult},
 * same shape.
 */
public record CandidateRegistrationResponse(UUID id, UUID personId, UUID admissionConfigId, String folio,
		CandidateStatus status, boolean llaveMxVerified, Instant registeredAt, boolean isFirstChoice,
		UUID outreachChannelId, boolean isEnabledForInduction, PaymentResponse payment) {

	/**
	 * The admission-ticket payment generated with the registration.
	 *
	 * <p>{@code registrationDeadline} is when the sales window closed (a
	 * snapshot, already past by the time this is read) and
	 * {@code paymentClosesOn} is when the tuition concept stops accepting
	 * payment (live, and enforced). The old single {@code deadline} was the
	 * former while the screen labelled it as the latter.
	 *
	 * <p>{@code paymentDeadline} is the date the screen states for "Fecha límite
	 * de pago": the earlier of {@code registrationDeadline} and the ficha's own
	 * plazo. It is not {@code paymentClosesOn}.
	 */
	public record PaymentResponse(String referenceNumber, BigDecimal amount, LocalDate registrationDeadline,
			AdmissionPaymentStatus status, LocalDate paymentClosesOn, LocalDate paymentDeadline) {
	}

	public static CandidateRegistrationResponse from(
			RegisterCandidateUseCase.CandidateRegistrationResult result) {
		RegisterCandidateUseCase.FichaPayment payment = result.payment();
		return new CandidateRegistrationResponse(result.id(), result.personId(), result.admissionConfigId(),
				result.folio(), result.status(), result.llaveMxVerified(), result.registeredAt(),
				result.isFirstChoice(), result.outreachChannelId(), result.isEnabledForInduction(),
				new PaymentResponse(payment.referenceNumber(), payment.amount(), payment.registrationDeadline(),
						payment.paymentStatus(), payment.paymentClosesOn(), payment.paymentDeadline()));
	}
}