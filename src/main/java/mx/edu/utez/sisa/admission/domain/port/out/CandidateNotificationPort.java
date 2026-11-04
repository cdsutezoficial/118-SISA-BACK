package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;

/**
 * What the candidate is told about their ficha (F3).
 *
 * <p>Called from inside the transaction that made the change, and delivered only
 * after it commits: a payment that rolls back sends nothing, and a mail that fails
 * never undoes a payment. Every path that settles a ficha calls it, so a receipt
 * goes out whether the browser, the nightly reconciliation, the saldo a favor or a
 * 100 % discount settled it.
 */
public interface CandidateNotificationPort {

	/** A ficha, or the difference a career change left, was paid. */
	void paymentConfirmed(ConfirmPaymentResult result);

	/** A career change left a difference to pay. */
	void programChangeLeftDifference(UUID candidateId, BigDecimal newAmount, BigDecimal alreadyPaid,
			BigDecimal toPay, Instant dueAt);

	/** A career change left money over, kept as saldo a favor. */
	void programChangeLeftCredit(UUID candidateId, BigDecimal credit);
}
