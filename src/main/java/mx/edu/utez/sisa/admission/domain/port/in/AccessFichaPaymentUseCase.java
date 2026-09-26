package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Vuelve a pagar mi ficha" access: the applicant who registered but did NOT
 * pay (or walked away from the checkout) comes back later and identifies
 * herself with {@code folio} + the last 3 characters of her {@code CURP}
 * ({@code POST /candidates/payment-access}). On success it hands back exactly
 * what the payment screen needs and NOTHING else.
 *
 * <p>Why a separate entry point at all: the post-registration screen
 * ({@code GET /candidates/{id}}, reached at {@code /portal/registro/ficha?id=…})
 * only exists while the applicant still holds the navigate-state UUID from the
 * registration. Reload loses it and there is no way back in — which is what
 * this use case fixes. It is the second of the two access paths the business
 * asked for, and it is intentionally a different, weaker proof of identity than
 * the UUID.
 *
 * <p><b>SECURITY — the projection is deliberately minimal.</b> Folios are
 * sequential ({@code ADM-2026-000101}) and a 3-character CURP suffix has ~46k
 * combinations, so this endpoint is brute-forceable by design; the owner
 * accepted that trade-off for a "return to pay" convenience. Two things keep it
 * from being a data breach:
 * <ol>
 * <li>the result carries payment fields only — no address, no health profile,
 * no income, no grade point average. Those live in {@code FichaData} and are
 * served by {@link GetCandidateFichaUseCase}, which stays UUID-only;</li>
 * <li>{@link #access} answers a wrong folio and a wrong CURP with the SAME
 * {@code CandidateNotFoundException} message, so the endpoint cannot be used to
 * enumerate which folios exist.</li>
 * </ol>
 * Brute force is further throttled at the web layer by
 * {@code PaymentAccessRateLimiter} (per-IP sliding window → 429).
 */
public interface AccessFichaPaymentUseCase {

	PaymentAccess access(String folio, String curpSuffix);

	/**
	 * The minimum the payment screen renders and the checkout needs. Note
	 * {@code candidateId}: the existing EVO endpoints
	 * ({@code POST /candidates/{id}/payments/checkout|confirm}) are keyed by it,
	 * so a successful access effectively hands back the same capability the
	 * post-registration screen holds — which is why the payment screen must not
	 * become a way to read the full ficha.
	 *
	 * <p>{@code paidAt} / {@code receiptNumber} are the only fields that carry
	 * evidence of a completed payment, so an applicant returning to an
	 * already-paid ficha still sees her confirmation date and receipt folio
	 * without having to open the full ficha.
	 */
	record PaymentAccess(UUID candidateId, String folio, String nombre, String programName, BigDecimal amount,
			String referenceNumber, LocalDate deadline, AdmissionPaymentStatus paymentStatus, String receiptNumber,
			Instant paidAt, boolean alreadyPaid) {
	}
}
