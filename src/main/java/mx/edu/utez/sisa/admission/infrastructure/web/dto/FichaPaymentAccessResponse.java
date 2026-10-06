package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
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
 *
 * <p>{@code paymentDeadline} is what makes the checkout button honest: it is the
 * earlier of the sales window and the ficha's own plazo, i.e. the boundary the
 * checkout endpoint will actually enforce, so the screen can never promise more
 * time than the backend gives. {@code paymentClosesOn} stays as the catalog's
 * own closing date for completeness but is no longer the date shown.
 *
 * <p>{@code paymentExpired} and {@code candidateStatus} are separate because they
 * answer different questions: the status is what the nightly VENCEN_FICHAS sweep
 * wrote down, the flag is what is true today. In the ten minutes between a
 * deadline passing and the sweep running they disagree, and the flag is the one
 * the screen must obey, because it is what the checkout enforces.
 */
public record FichaPaymentAccessResponse(UUID candidateId, String folio, String nombre, String programName,
		BigDecimal amount, String referenceNumber, LocalDate registrationDeadline, AdmissionPaymentStatus paymentStatus,
		String receiptNumber, Instant paidAt, boolean alreadyPaid, LocalDate paymentClosesOn,
		LocalDate paymentDeadline, CandidateStatus candidateStatus, boolean paymentExpired) {

	public static FichaPaymentAccessResponse from(PaymentAccess access) {
		return new FichaPaymentAccessResponse(access.candidateId(), access.folio(), access.nombre(),
				access.programName(), access.amount(), access.referenceNumber(), access.registrationDeadline(),
				access.paymentStatus(), access.receiptNumber(), access.paidAt(), access.alreadyPaid(),
				access.paymentClosesOn(), access.paymentDeadline(), access.candidateStatus(),
				access.paymentExpired());
	}
}
