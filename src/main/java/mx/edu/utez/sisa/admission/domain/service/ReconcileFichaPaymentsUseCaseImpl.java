package mx.edu.utez.sisa.admission.domain.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ReconcileFichaPaymentsUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;

/**
 * {@link ReconcileFichaPaymentsUseCase}: every open attempt, one question to the bank.
 *
 * <p>Deliberately not {@code @Transactional}. It calls a third party once per attempt,
 * so holding a transaction across the loop would keep one open for as long as the gateway
 * takes, and roll back forty settled payments because attempt forty-one timed out. Every
 * write here is its own short transaction inside {@link CheckoutSlotClaimer}, which is
 * what lets a half-finished sweep still be a correct one.
 *
 * <p>Per attempt, one {@code Retrieve Order} through {@link OrderSettlementDecider}:
 * <ul>
 * <li><b>captured</b> — the ficha is marked paid and the attempt closes {@code CAPTURED}.
 * This is the branch that earns the whole mechanism its keep: it is how a payment that
 * landed after the applicant stopped looking still becomes {@code PAID}.</li>
 * <li><b>refused</b> — the bank took nothing, so the place goes back and the attempt
 * closes {@code REJECTED}.</li>
 * <li><b>anything else</b> — nothing is written and the attempt stays open, to be asked
 * again tomorrow.</li>
 * </ul>
 *
 * <p>The bank is asked even about attempts belonging to an already-paid ficha. Skipping
 * them would be simpler and wrong twice over: it would leave those rows open forever,
 * because nothing invents a "superseded" reason for an order the bank never refused, and
 * it would decide an attempt's fate from our own bookkeeping instead of from the only
 * party that knows. What a paid ficha skips is the {@code release} — its place is
 * permanently its own.
 */
public class ReconcileFichaPaymentsUseCaseImpl implements ReconcileFichaPaymentsUseCase {

	private static final Logger log = LoggerFactory.getLogger(ReconcileFichaPaymentsUseCaseImpl.class);

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	private final ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	public ReconcileFichaPaymentsUseCaseImpl(CheckoutAttemptRepository checkoutAttemptRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			CheckoutSlotClaimer checkoutSlotClaimer,
			ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase) {
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
		this.confirmFichaPaymentVerifiedUseCase = confirmFichaPaymentVerifiedUseCase;
	}

	@Override
	public ReconciliationResult reconcile() {
		int captures = 0;
		int releases = 0;
		int closedOnly = 0;
		int held = 0;
		int failed = 0;

		for (CheckoutAttempt attempt : checkoutAttemptRepository.findOpenAttempts()) {
			if (!attempt.isOpen()) {
				// Raced with the browser between the query and now. The first answer is
				// the one that was true, and re-deciding it would be precisely the
				// overwrite that `CheckoutAttempt#close` refuses to perform.
				continue;
			}
			try {
				switch (settle(attempt)) {
					case CAPTURED -> captures++;
					case RELEASED -> releases++;
					case CLOSED_ONLY -> closedOnly++;
					case HELD -> held++;
				}
			} catch (EvoPaymentGatewayException ex) {
				// The bank was unreachable or answered something unusable. This is the
				// one failure the sweep must never turn into a release: "we could not
				// ask" and "the bank refused" have to stay distinguishable, or an outage
				// hands away places whose payments are still in flight.
				failed++;
				log.warn("No se pudo conciliar el intento {} ({}): {}", attempt.getOrderId(), attempt.getCandidateId(),
						ex.getMessage());
			} catch (RuntimeException ex) {
				// Anything else — a missing ficha, an unexpected state transition. Counted
				// and survived: one poisoned attempt must not abandon the other thirty-nine.
				failed++;
				log.error("Fallo inesperado conciliando el intento {} ({}): {}", attempt.getOrderId(),
						attempt.getCandidateId(), ex.getMessage(), ex);
			}
		}

		return new ReconciliationResult(captures, releases, captures + releases + closedOnly, held, failed);
	}

	/** What the sweep did with one attempt, which is also what it counts. */
	private enum Outcome {
		/** Money arrived; the ficha is now {@code PAID}. */
		CAPTURED,
		/** The bank refused a pending ficha; its place went back. */
		RELEASED,
		/** The row was closed but no quota changed, because the ficha is already paid. */
		CLOSED_ONLY,
		/** Not conclusive. Nothing written, asked again tomorrow. */
		HELD
	}

	private Outcome settle(CheckoutAttempt attempt) {
		EvoPaymentsGatewayPort.EvoOrderStatus status = evoPaymentsGateway.retrieveOrder(attempt.getOrderId());

		// Read once per attempt: the release branch must not write a PAID ficha, and the
		// capture branch must not re-confirm one.
		boolean alreadyPaid = isPaid(attempt.getCandidateId());

		return switch (OrderSettlementDecider.decide(status)) {
			case CAPTURED -> alreadyPaid ? closeAs(attempt, CheckoutAttemptCloseReason.CAPTURED)
					: confirmAsPaid(attempt);
			case RELEASEABLE -> alreadyPaid ? closeAs(attempt, CheckoutAttemptCloseReason.REJECTED)
					: releaseAndClose(attempt);
			case HELD_UNKNOWN -> Outcome.HELD;
		};
	}

	/**
	 * Delegates to the same confirmation the browser path uses, so there is exactly one
	 * definition of "this ficha is paid" — receipt, candidate status and the {@code
	 * CAPTURED} attempt close included.
	 *
	 * <p>That single definition is the invariant this sweep rests on: a {@code PAID} ficha
	 * never has an open attempt, which is what stops tomorrow's run from asking about it
	 * again. Before the {@code CAPTURED} close existed, nothing ever wrote it, so every
	 * paid ficha left an attempt open for good and the sweep re-asked the bank about it
	 * every night, forever.
	 */
	private Outcome confirmAsPaid(CheckoutAttempt attempt) {
		confirmFichaPaymentVerifiedUseCase.confirm(attempt.getCandidateId(), attempt.getOrderId());
		return Outcome.CAPTURED;
	}

	private Outcome releaseAndClose(CheckoutAttempt attempt) {
		checkoutSlotClaimer.release(attempt.getCandidateId());
		checkoutSlotClaimer.closeAttempt(attempt.getOrderId(), CheckoutAttemptCloseReason.REJECTED);
		return Outcome.RELEASED;
	}

	/**
	 * Closes the row without touching the quota. The reason is still the bank's verdict
	 * and not our guess about the sibling attempt: an order the bank refused is recorded
	 * as refused whether or not the applicant later succeeded with a different one.
	 */
	private Outcome closeAs(CheckoutAttempt attempt, CheckoutAttemptCloseReason reason) {
		checkoutSlotClaimer.closeAttempt(attempt.getOrderId(), reason);
		return Outcome.CLOSED_ONLY;
	}

	private boolean isPaid(UUID candidateId) {
		return admissionPaymentRepository.findByCandidateId(candidateId)
				.map(payment -> payment.getPaymentStatus() == AdmissionPaymentStatus.PAID).orElse(false);
	}
}