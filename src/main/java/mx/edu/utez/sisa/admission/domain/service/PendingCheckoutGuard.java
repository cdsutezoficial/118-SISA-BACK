package mx.edu.utez.sisa.admission.domain.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.AdmissionPaidWithoutQuotaException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentInProgressException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;

/**
 * One online session per ficha (E1, prevention): before a new checkout is
 * opened, the bank is asked about every attempt the candidate still has open.
 *
 * <ul>
 * <li><b>captured</b> on the charge being collected — it is applied now and no
 * new session is opened; captured on any other charge — it becomes saldo a
 * favor and its row is closed;</li>
 * <li><b>refused</b> — the row is closed and the new session goes ahead;</li>
 * <li><b>authorized but not captured</b> — no new session: the bank holds money
 * that can still be captured, so both could end up charged;</li>
 * <li><b>anything else unresolved</b> — the new session goes ahead and the old
 * row stays open for the nightly reconciliation.</li>
 * </ul>
 *
 * <p>Why "unresolved" alone does not block: the gateway answers the same for a
 * session the applicant opened and closed without typing a card as for one
 * still running — no capture and no refusal. Blocking on that locked the
 * applicant out for good (seen in local testing, 2026-11-03), because the
 * reconciliation keeps such a row open too. An authorization is the only
 * answer that proves money is actually in flight; for the rest, the
 * overpayment path books a late capture as saldo a favor.
 *
 * <p>An attempt the bank cannot be asked about (an outage, or an order the
 * gateway does not know) neither blocks nor is closed: it stays open for the
 * nightly reconciliation, and if it ever captures, the overpayment path books
 * it. Blocking on it would lock the applicant out until that night for a
 * session she may simply have closed.
 *
 * <p>The gateway EVO uses cannot cancel a session, which is why an old one is
 * resolved instead of cancelled.
 *
 * <p>Not transactional: it calls the gateway, and every write goes through its
 * own short transaction.
 */
public class PendingCheckoutGuard {

	private static final Logger log = LoggerFactory.getLogger(PendingCheckoutGuard.class);

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final FichaPaymentPort fichaPaymentPort;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	private final ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	public PendingCheckoutGuard(CheckoutAttemptRepository checkoutAttemptRepository,
			EvoPaymentsGatewayPort evoPaymentsGateway, FichaPaymentPort fichaPaymentPort,
			CheckoutSlotClaimer checkoutSlotClaimer,
			ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase) {
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.fichaPaymentPort = fichaPaymentPort;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
		this.confirmFichaPaymentVerifiedUseCase = confirmFichaPaymentVerifiedUseCase;
	}

	/**
	 * Resolves the candidate's open attempts before a new session is opened.
	 *
	 * @throws CandidateAlreadyPaidException if an earlier attempt already paid
	 *                                       what is being collected
	 * @throws PaymentInProgressException    if an earlier attempt is still
	 *                                       unresolved at the bank
	 */
	public void requireNoPaymentInFlight(Candidate candidate) {
		List<CheckoutAttempt> open = checkoutAttemptRepository.findOpenAttemptsByCandidateId(candidate.getId());
		boolean inFlight = false;
		boolean paid = false;
		for (CheckoutAttempt attempt : open) {
			Optional<EvoPaymentsGatewayPort.EvoOrderStatus> answer = ask(attempt);
			if (answer.isEmpty()) {
				continue;
			}
			switch (OrderSettlementDecider.decide(answer.get())) {
				case CAPTURED -> paid |= applyCapture(candidate, attempt, answer.get());
				case RELEASEABLE -> checkoutSlotClaimer.closeAttempt(attempt.getOrderId(),
						CheckoutAttemptCloseReason.REJECTED);
				case HELD_UNKNOWN -> inFlight |= authorizedNotCaptured(answer.get());
			}
		}
		if (paid) {
			throw new CandidateAlreadyPaidException(
					"Tu pago anterior ya se confirmó: la ficha " + candidate.getFolio() + " está pagada.");
		}
		if (inFlight) {
			throw new PaymentInProgressException(
					"Tienes un pago en proceso con el banco. Espera a que se confirme antes de intentar de nuevo.");
		}
	}

	private static boolean authorizedNotCaptured(EvoPaymentsGatewayPort.EvoOrderStatus answer) {
		return OrderSettlementDecider.authorizedNotCaptured(answer);
	}

	private Optional<EvoPaymentsGatewayPort.EvoOrderStatus> ask(CheckoutAttempt attempt) {
		try {
			return Optional.of(evoPaymentsGateway.retrieveOrder(attempt.getOrderId()));
		} catch (EvoPaymentGatewayException ex) {
			log.warn("No se pudo consultar la orden {} antes de abrir otra sesión: {}", attempt.getOrderId(),
					ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * @return {@code true} when the capture paid what is being collected now
	 */
	private boolean applyCapture(Candidate candidate, CheckoutAttempt attempt,
			EvoPaymentsGatewayPort.EvoOrderStatus answer) {
		boolean collecting = attempt.getPaymentId().equals(candidate.collectingPaymentId());
		boolean owed = fichaPaymentPort.findCharge(attempt.getPaymentId())
				.map(charge -> !charge.isPaid() && !charge.cancelled()).orElse(false);
		if (collecting && owed) {
			try {
				confirmFichaPaymentVerifiedUseCase.confirm(candidate.getId(), attempt.getOrderId());
			} catch (CandidateAlreadyPaidException ex) {
				// Settled by the browser a moment ago: paid either way.
			} catch (AdmissionPaidWithoutQuotaException ex) {
				// E4: kept as saldo a favor and the attempt closed. Nothing was paid.
				return false;
			}
			return true;
		}
		fichaPaymentPort.registerOverpayment(attempt.getPaymentId(), attempt.getOrderId(),
				answer.totalCapturedAmount());
		checkoutSlotClaimer.closeAttempt(attempt.getOrderId(), CheckoutAttemptCloseReason.CAPTURED);
		return false;
	}
}
