package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseOutcome;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseResult;

import java.util.UUID;

/**
 * Body for {@code POST /candidates/{id}/payments/release} — what the gateway
 * actually said about the attempt, and whether the quota slot went back.
 *
 * <p>Both fields travel to the portal because they are different facts and the
 * applicant needs to hear different things about each. {@code slotReleased} false
 * with {@code outcome=SLOT_RELEASED} is impossible; the reverse is the common case:
 * nothing was freed because something else turned out to be true. A single boolean
 * would collapse "the place is yours again, retry" and "your money is already in"
 * into one indistinguishable answer, and the portal would have to guess.
 */
public record ReleaseFichaPaymentResponse(UUID candidateId, String orderId, ReleaseOutcome outcome,
		boolean slotReleased) {

	public static ReleaseFichaPaymentResponse from(ReleaseResult result) {
		return new ReleaseFichaPaymentResponse(result.candidateId(), result.orderId(), result.outcome(),
				result.slotReleased());
	}
}
