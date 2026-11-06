package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short, locked half of confirming a capture the bank already confirmed.
 *
 * <p>{@link ConfirmFichaPaymentVerifiedUseCaseImpl} asks EVO first, with no transaction
 * open, and only then calls this. Here the charge is locked and read again: two captures
 * of one ficha seconds apart serialize on that lock, the first settles the charge and the
 * second finds it paid and becomes saldo a favor (E1). Nothing in here calls the gateway,
 * so the lock is held for the database work only, never for a network call.
 *
 * <p>Its own bean so the transaction goes through the Spring proxy.
 */
public class CapturedPaymentSettler {

	private final FichaPaymentPort fichaPaymentPort;

	private final ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	public CapturedPaymentSettler(FichaPaymentPort fichaPaymentPort,
			ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase) {
		this.fichaPaymentPort = fichaPaymentPort;
		this.confirmAdmissionPaymentUseCase = confirmAdmissionPaymentUseCase;
	}

	/**
	 * Locks the charge and either settles it with this order's capture or, if another
	 * capture paid it first, books this one as saldo a favor (idempotent by order).
	 * {@code READ_COMMITTED} so what is read after waiting on the lock is what the other
	 * confirmation committed.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Outcome settle(UUID candidateId, UUID chargeId, String orderId, BigDecimal captured) {
		FichaPaymentPort.FichaCharge charge = fichaPaymentPort.lockCharge(chargeId).orElse(null);
		if (charge != null && charge.isPaid()) {
			return Outcome.alreadyPaid(fichaPaymentPort.registerOverpayment(chargeId, orderId, captured));
		}
		return Outcome.settled(confirmAdmissionPaymentUseCase.confirm(candidateId, orderId));
	}

	/**
	 * What happened under the lock: the charge was settled by this order, or it was
	 * already paid and {@code creditBooked} says whether this order's capture became saldo
	 * a favor ({@code false} when this order is the one that paid it).
	 */
	public record Outcome(ConfirmPaymentResult result, boolean creditBooked) {

		static Outcome settled(ConfirmPaymentResult result) {
			return new Outcome(result, false);
		}

		static Outcome alreadyPaid(boolean creditBooked) {
			return new Outcome(null, creditBooked);
		}

		public boolean wasAlreadyPaid() {
			return result == null;
		}
	}
}
