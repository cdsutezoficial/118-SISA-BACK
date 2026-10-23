package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;

import java.time.Instant;
import java.util.UUID;

/**
 * Raises a charge (adeudo) as a <b>header only</b>: who owes, which catalog
 * concept and which program prices it. No tariff and no discount are
 * stored; the charge is priced live whenever it is looked at
 * ({@link QuoteDebtUseCase}) and frozen only when the payer acts
 * ({@link FreezeDebtQuoteUseCase}).
 *
 * <p>Generic on purpose: an aspirante's ficha and a student's tuition are the
 * same thing here — a charge of a catalog concept to an anchor.
 *
 * <p>Idempotent per {@code (anchor, conceptCode)}: if the anchor already has
 * an open charge for that concept it is returned instead of a second one.
 */
public interface RaiseDebtUseCase {

	UUID raise(RaiseDebtCommand command);

	/**
	 * @param candidateId  the aspirante that owes it (exactly one anchor)
	 * @param studentId    the student that owes it (exactly one anchor)
	 * @param conceptCode  the catalog {@code PaymentConcept.code} — never a
	 *                     hard-coded label
	 * @param programId    the program that prices it
	 * @param periodId     optional period anchor
	 * @param generationId optional generation anchor
	 * @param origin       which flow raised it
	 * @param dueAt        the deadline, when there is one
	 * @param referenceNumber the reference the payer quotes, when the flow has one
	 */
	record RaiseDebtCommand(UUID candidateId, UUID studentId, String conceptCode, UUID programId, UUID periodId,
			UUID generationId, PaymentOrigin origin, Instant dueAt, String referenceNumber) {
	}
}
