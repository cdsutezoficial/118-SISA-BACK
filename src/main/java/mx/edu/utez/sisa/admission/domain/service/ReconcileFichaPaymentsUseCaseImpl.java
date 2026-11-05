package mx.edu.utez.sisa.admission.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ReconcileFichaPaymentsUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.FinanceAlertPort;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.AdmissionPaidWithoutQuotaException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;

/**
 * {@link ReconcileFichaPaymentsUseCase}: every open attempt, one question to the bank,
 * decided <em>per ficha</em>.
 *
 * <p>Deliberately not {@code @Transactional}. It calls a third party once per attempt,
 * so holding a transaction across the loop would keep one open for as long as the gateway
 * takes, and roll back forty settled payments because attempt forty-one timed out. Every
 * write here is its own short transaction inside {@link CheckoutSlotClaimer} or the
 * confirmation interactor, which is what lets a half-finished sweep still be a correct
 * one.
 *
 * <p><b>Counts are attempts, and a failed ficha spends all of its own.</b> One order that
 * could not be asked about takes its ficha's whole row count as failed, not just itself,
 * because the group is the unit of decision: nothing about that ficha could be settled, so
 * its {@code failedAttempts} equals its {@code heldAttempts}. The per-ficha detail is in the
 * log line, where the number can be read without having to guess how it was counted.
 *
 * <p><b>Two phases per ficha, and the order is the fix.</b> Everything is asked before
 * anything is written. The sweep used to decide one attempt at a time and write as it
 * went, which was wrong in a way no single attempt's verdict could reveal: a ficha's
 * {@code orderId} is overwritten on every retry, so she can own several open attempts,
 * and her money is hers rather than any attempt's. Asked in whatever order the query
 * returned them, an old {@code FAILURE} released her place and a sibling capture then
 * marked her paid — a sold place given away and a paid applicant left without one. The
 * same oversell the quota mechanism exists to prevent, rebuilt one layer up.
 *
 * <p>So the sweep groups by {@code candidateId}, asks about every order in the group,
 * folds the verdicts through {@link OrderSettlementDecider#decideFicha} and only then
 * writes:
 * <ul>
 * <li><b>any capture</b> — the ficha is marked paid through the same confirmation the
 * browser path uses, her place stays hers, and the sibling rows are closed on what the
 * bank said about <em>each</em> of them. This is the branch that earns the whole
 * mechanism its keep: it is how a payment that landed after the applicant stopped looking
 * still becomes {@code PAID}.</li>
 * <li><b>every attempt refused</b> — nothing is in flight, so the attempts are closed,
 * which is what gives the place back (an open attempt is what holds it).</li>
 * <li><b>anything unanswered</b> — nothing is written and every row stays open, to be
 * asked again tomorrow. That includes a group where one order could not even be asked
 * about: an unread attempt makes the picture incomplete, and an unreadable one is not a
 * refusal.</li>
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

	private final FichaPaymentPort fichaPaymentPort;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	private final ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	/** How long an authorization may wait for its capture before Finanzas is alerted (E4). */
	static final Duration AUTHORIZED_ALERT_AFTER = Duration.ofDays(3);

	private final Clock clock;

	private final FinanceAlertPort financeAlertPort;

	/**
	 * D10: an attempt the bank has nothing for (no capture, no authorization) and older
	 * than this is an abandoned session. Same value as the checkout hold, so the place
	 * and the attempt end together.
	 */
	private final Duration abandonAfter;

	public ReconcileFichaPaymentsUseCaseImpl(CheckoutAttemptRepository checkoutAttemptRepository,
			FichaPaymentPort fichaPaymentPort, EvoPaymentsGatewayPort evoPaymentsGateway,
			CheckoutSlotClaimer checkoutSlotClaimer,
			ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase, Clock clock,
			int abandonAfterMinutes, FinanceAlertPort financeAlertPort) {
		this.financeAlertPort = financeAlertPort;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
		this.confirmFichaPaymentVerifiedUseCase = confirmFichaPaymentVerifiedUseCase;
		this.clock = clock;
		this.abandonAfter = Duration.ofMinutes(abandonAfterMinutes);
	}

	@Override
	public ReconciliationResult reconcile() {
		Counters counters = new Counters();

		for (Map.Entry<UUID, List<CheckoutAttempt>> ficha : openAttemptsByFicha().entrySet()) {
			try {
				settleFicha(ficha.getKey(), ficha.getValue(), counters);
			} catch (EvoPaymentGatewayException ex) {
				// The bank was unreachable or answered something unusable. This is the
				// one failure the sweep must never turn into a release: "we could not
				// ask" and "the bank refused" have to stay distinguishable, or an outage
				// hands away places whose payments are still in flight. Nothing is
				// written for the ficha, and every one of its rows is left for tomorrow.
				counters.failed(ficha.getValue().size());
				counters.held(ficha.getValue().size());
				log.warn("No se pudo conciliar la ficha {} ({} intento(s)): {}", ficha.getKey(),
						ficha.getValue().size(), ex.getMessage());
			} catch (RuntimeException ex) {
				// Anything else — a missing ficha, an unexpected state transition. Counted
				// and survived: one poisoned ficha must not abandon the other thirty-nine.
				counters.failed(ficha.getValue().size());
				counters.held(ficha.getValue().size());
				log.error("Fallo inesperado conciliando la ficha {} ({} intento(s)): {}", ficha.getKey(),
						ficha.getValue().size(), ex.getMessage(), ex);
			}
		}

		return counters.toResult();
	}

	/**
	 * The rows the query returned, grouped by the ficha that owns them.
	 *
	 * <p>A row that came back already closed stays in its group, and that is deliberate on
	 * both sides of the split. It is still read, because a row closed as {@code CAPTURED} is
	 * a record that money moved: dropping it would let the one attempt that is still open
	 * decide the ficha's quota alone, and a {@code FAILURE} there would free a place whose
	 * payment had already landed. It is never written, since {@code close} refuses to
	 * overwrite a settled row and this class never tries to force it.
	 *
	 * <p>The repository query is over open rows, so in practice every group here is open;
	 * the ones that are not arrived in the seconds between the query and this loop, which is
	 * the race the closed-row handling is written for.
	 */
	private Map<UUID, List<CheckoutAttempt>> openAttemptsByFicha() {
		Map<UUID, List<CheckoutAttempt>> byFicha = new LinkedHashMap<>();
		for (CheckoutAttempt attempt : checkoutAttemptRepository.findOpenAttempts()) {
			if (attempt.getCandidateId() == null) {
				continue; // not a ficha: student charges are not reconciled here
			}
			byFicha.computeIfAbsent(attempt.getCandidateId(), key -> new ArrayList<>()).add(attempt);
		}
		return byFicha;
	}

	/**
	 * Phase 1: ask about every order this ficha has open, and remember the answers.
	 *
	 * <p>Nothing is written here, not even for the attempt whose answer is already clear.
	 * That restraint is the entire point — the moment a refusal is allowed to release the
	 * place, a sibling capture one row later becomes a paid ficha with no slot, and no
	 * single verdict in this method looked wrong.
	 *
	 * <p>A failed lookup fails the whole group. Not a nearby line: the missing answer is
	 * precisely the one that would tell us whether this ficha's money is already hers.
	 */
	private Map<CheckoutAttempt, EvoPaymentsGatewayPort.EvoOrderStatus> askBankAboutEveryAttempt(
			List<CheckoutAttempt> attempts) {
		Map<CheckoutAttempt, EvoPaymentsGatewayPort.EvoOrderStatus> answers = new LinkedHashMap<>();
		for (CheckoutAttempt attempt : attempts) {
			answers.put(attempt, evoPaymentsGateway.retrieveOrder(attempt.getOrderId()));
		}
		return answers;
	}

	/**
	 * Phase 2: act on the fold.
	 *
	 * <p>Every charge the group's attempts point at is read once, here. A candidate can
	 * hold attempts of more than one charge — the paid ficha and the difference a career
	 * change left — so "is it paid?" is asked of each attempt's own charge, never of
	 * whichever row the query returned first. An already-paid ficha is settled on the
	 * answers alone — nothing about her place is touched, ever.
	 */
	private void settleFicha(UUID candidateId, List<CheckoutAttempt> attempts, Counters counters) {
		Map<CheckoutAttempt, EvoPaymentsGatewayPort.EvoOrderStatus> answers = askBankAboutEveryAttempt(attempts);
		Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts = new LinkedHashMap<>();
		Map<CheckoutAttempt, CheckoutAttemptCloseReason> closeReasons = new HashMap<>();
		Instant now = Instant.now(clock);
		answers.forEach((attempt, answer) -> {
			OrderSettlementDecider.Verdict verdict = OrderSettlementDecider.decide(answer);
			if (verdict == OrderSettlementDecider.Verdict.HELD_UNKNOWN && isAbandoned(attempt, answer, now)) {
				// D10: the bank took nothing and the hold is over. That is a session
				// nobody finished, not a payment in flight, so it ends like a refusal.
				verdict = OrderSettlementDecider.Verdict.RELEASEABLE;
				closeReasons.put(attempt, CheckoutAttemptCloseReason.ORDER_EXPIRED);
			}
			alertIfAuthorizedTooLong(candidateId, attempt, answer, now);
			verdicts.put(attempt, verdict);
		});

		Map<UUID, Optional<FichaPaymentPort.FichaCharge>> charges = new HashMap<>();
		for (CheckoutAttempt attempt : attempts) {
			charges.computeIfAbsent(attempt.getPaymentId(), fichaPaymentPort::findCharge);
		}
		boolean holdsPaidCharge = charges.values().stream().flatMap(Optional::stream)
				.anyMatch(FichaPaymentPort.FichaCharge::isPaid);

		switch (OrderSettlementDecider.decideFicha(new ArrayList<>(verdicts.values()))) {
			case CAPTURED -> captureWon(candidateId, attempts, answers, verdicts, closeReasons, charges, counters);
			case RELEASEABLE -> releaseIsSafe(holdsPaidCharge, attempts, closeReasons, counters);
			case HELD_UNKNOWN -> counters.held(attempts.size());
		}
	}

	/**
	 * Money arrived for this ficha, whichever order it arrived on.
	 *
	 * <p>Marking her paid goes through the same interactor the browser path uses, so
	 * "this ficha is paid" stays defined once — receipt, candidate status and the
	 * {@code CAPTURED} close of the capturing attempt included. That single definition is
	 * the invariant this sweep rests on: a {@code PAID} ficha never has an open attempt,
	 * which is what stops tomorrow's run from asking about it again.
	 *
	 * <p>Every sibling row is then closed on <em>its own</em> verdict. Recording
	 * {@code CAPTURED} everywhere would make the counts prettier and put a lie on the
	 * rows; leaving them open would have tomorrow's run asking about money this one has
	 * already settled. One is refused and one captured, so one row is {@code REJECTED}
	 * and one is {@code CAPTURED}, and both are true.
	 *
	 * <p>A capture that settles nothing — its charge was already paid, by a sibling or
	 * earlier, or was cancelled — is money the bank holds and the ficha does not need. It
	 * becomes saldo a favor of the person, tied to its order, before the row is closed
	 * (E1). Finance books it once per order and skips the order that paid the charge.
	 */
	private void captureWon(UUID candidateId, List<CheckoutAttempt> attempts,
			Map<CheckoutAttempt, EvoPaymentsGatewayPort.EvoOrderStatus> answers,
			Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts,
			Map<CheckoutAttempt, CheckoutAttemptCloseReason> closeReasons,
			Map<UUID, Optional<FichaPaymentPort.FichaCharge>> charges, Counters counters) {
		CheckoutAttempt capturing = capturingAttempt(attempts, verdicts, charges);
		boolean settledHere = false;

		if (capturing != null) {
			try {
				confirmFichaPaymentVerifiedUseCase.confirm(candidateId, capturing.getOrderId());
				settledHere = true;
				counters.captured();
			} catch (CandidateAlreadyPaidException ex) {
				// The browser got there in the seconds between the query and this write.
				// That is a successful settlement that happened elsewhere, not a failure:
				// the payment is paid either way, and the only thing left to do is the
				// bookkeeping this sweep owns. Counted, because the money did arrive and
				// this row's work is done — a run that saw a capture and reported nothing
				// settled is the reading that teaches an operator to distrust the counter.
				log.info("La ficha {} ya estaba pagada al conciliarla; se cierra su intento {}", candidateId,
						capturing.getOrderId());
				settledHere = true;
				counters.captured();
			} catch (AdmissionPaidWithoutQuotaException ex) {
				// E4: a late capture found the career full. The confirmation already turned
				// the money into saldo a favor and closed the attempt; that is the outcome,
				// not a failure to retry tomorrow.
				log.warn("Captura tardía sin cupo en la ficha {}: {}", candidateId, ex.getMessage());
				settledHere = true;
				counters.closed(1);
			}
		}

		for (CheckoutAttempt attempt : attempts) {
			OrderSettlementDecider.Verdict verdict = verdicts.get(attempt);
			if (verdict == OrderSettlementDecider.Verdict.CAPTURED) {
				if (attempt == capturing && settledHere) {
					continue; // the confirmation closed it
				}
				if (!attempt.isOpen()) {
					continue; // already accounted for by whoever closed it
				}
				boolean paidBySibling = settledHere && attempt.getPaymentId().equals(capturing.getPaymentId());
				if (isPaid(charges, attempt) || paidBySibling || isCancelled(charges, attempt)) {
					bookOverpayment(candidateId, attempt, answers.get(attempt));
					closeAttempt(attempt, CheckoutAttemptCloseReason.CAPTURED, counters);
				} else {
					// Money on a charge that cannot be read, or still owed while another
					// capture is the one being confirmed. Closing the row would make the
					// money invisible, so it stays open to be asked about again.
					log.warn("Captura sin aplicar en la ficha {}: orden {} del cargo {}", candidateId,
							attempt.getOrderId(), attempt.getPaymentId());
					counters.held(1);
				}
			} else if (verdict == OrderSettlementDecider.Verdict.RELEASEABLE) {
				closeAttempt(attempt, refusalReason(closeReasons, attempt), counters);
			} else {
				// Still unanswered. It stays open, and the next run reads it as part of a
				// paid ficha, where closing it needs no further question.
				counters.held(1);
			}
		}
	}

	/**
	 	 * Every attempt of this ficha is refused, so nothing is in flight and the place goes
	 * back by closing those attempts.
	 *
	 * <p>Only the rows read by this run are closed. Between asking the bank and writing,
	 * the applicant may have started another checkout; that new attempt is not in this
	 * group, so it keeps holding its place — the same guarantee the old compare-and-set
	 * on the claim gave.
	 */
	private void releaseIsSafe(boolean alreadyPaid, List<CheckoutAttempt> attempts,
			Map<CheckoutAttempt, CheckoutAttemptCloseReason> closeReasons, Counters counters) {
		// The open attempts are what hold the place, so closing them is the release.
		// Only the rows this run asked about are closed: an attempt opened after the
		// query is not in this group and keeps holding its place.
		for (CheckoutAttempt attempt : attempts) {
			closeAttempt(attempt, refusalReason(closeReasons, attempt), counters);
		}
		if (!alreadyPaid) {
			counters.released();
		}
	}

	/**
	 * The attempt to hand to the confirmation: one the bank says captured, one nobody
	 * has settled yet, and one whose own charge is still waiting for that money.
	 *
	 * <p>All three are load-bearing. Without the first, an already-paid ficha would be
	 * re-confirmed. Without the second, a capture whose row the browser closed a moment
	 * ago would be thrown at a confirmation that only accepts an open attempt, and the
	 * run would log a failure for a payment that had already been settled correctly.
	 * Without the third, a capture on the paid ficha would hide a capture on the
	 * difference a career change left.
	 */
	private CheckoutAttempt capturingAttempt(List<CheckoutAttempt> attempts,
			Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts,
			Map<UUID, Optional<FichaPaymentPort.FichaCharge>> charges) {
		return attempts.stream().filter(attempt -> verdicts.get(attempt) == OrderSettlementDecider.Verdict.CAPTURED)
				.filter(CheckoutAttempt::isOpen)
				.filter(attempt -> charges.get(attempt.getPaymentId())
						.map(charge -> !charge.isPaid() && !charge.cancelled()).orElse(false))
				.findFirst().orElse(null);
	}

	/**
	 * Books what this order captured as saldo a favor. Finance answers {@code false} for
	 * the order that paid the charge (it already has its abono), which is the browser
	 * having settled it a moment ago, not money left over.
	 */
	private void bookOverpayment(UUID candidateId, CheckoutAttempt attempt,
			EvoPaymentsGatewayPort.EvoOrderStatus answer) {
		if (fichaPaymentPort.registerOverpayment(attempt.getPaymentId(), attempt.getOrderId(),
				answer.totalCapturedAmount())) {
			log.warn("Excedente de la ficha {}: la orden {} capturó {} sin liquidar nada; queda como saldo a favor",
					candidateId, attempt.getOrderId(), answer.totalCapturedAmount());
			financeAlertPort.raise(FinanceAlertPort.Kind.OVERPAYMENT, candidateId, attempt.getPaymentId(),
					attempt.getOrderId(), answer.totalCapturedAmount(),
					"El banco cobró un pago que no liquidó ningún cargo (segundo cobro o cargo cancelado). "
							+ "Quedó como saldo a favor de la persona.");
		}
	}

	private static boolean isCancelled(Map<UUID, Optional<FichaPaymentPort.FichaCharge>> charges,
			CheckoutAttempt attempt) {
		return charges.get(attempt.getPaymentId()).map(FichaPaymentPort.FichaCharge::cancelled).orElse(false);
	}

	private static boolean isPaid(Map<UUID, Optional<FichaPaymentPort.FichaCharge>> charges,
			CheckoutAttempt attempt) {
		return charges.get(attempt.getPaymentId()).map(FichaPaymentPort.FichaCharge::isPaid).orElse(false);
	}

	private static CheckoutAttemptCloseReason refusalReason(
			Map<CheckoutAttempt, CheckoutAttemptCloseReason> closeReasons, CheckoutAttempt attempt) {
		return closeReasons.getOrDefault(attempt, CheckoutAttemptCloseReason.REJECTED);
	}

	/** D10: nothing taken at the bank and older than the hold. */
	private boolean isAbandoned(CheckoutAttempt attempt, EvoPaymentsGatewayPort.EvoOrderStatus answer, Instant now) {
		return OrderSettlementDecider.tookNothing(answer)
				&& !attempt.getCreatedAt().isAfter(now.minus(abandonAfter));
	}

	/**
	 * E4: an authorization the bank has not captured is waited on, never closed, but not
	 * silently forever. Past three days it is an error line for Finanzas to review by hand.
	 */
	private void alertIfAuthorizedTooLong(UUID candidateId, CheckoutAttempt attempt,
			EvoPaymentsGatewayPort.EvoOrderStatus answer, Instant now) {
		if (OrderSettlementDecider.authorizedNotCaptured(answer)
				&& attempt.getCreatedAt().isBefore(now.minus(AUTHORIZED_ALERT_AFTER))) {
			log.error("ALERTA FINANZAS: la orden {} de la ficha {} tiene {} autorizado sin cobrar desde {}. "
					+ "Revisar a mano con el banco.", attempt.getOrderId(), candidateId,
					answer.totalAuthorizedAmount(), attempt.getCreatedAt());
			financeAlertPort.raise(FinanceAlertPort.Kind.AUTHORIZED_NOT_CAPTURED, candidateId, attempt.getPaymentId(),
					attempt.getOrderId(), answer.totalAuthorizedAmount(),
					"El banco autorizó el monto y no lo ha cobrado desde " + attempt.getCreatedAt()
							+ ". Revisar con el banco si se cobra o se libera.");
		}
	}

	/**
	 * Closes a row and counts it, or leaves an already-closed row alone and counts nothing.
	 *
	 * <p>Counting only what was written is the point: {@code closedRows} is what the office
	 * reads to know whether the run still has work left, and a row that needed no write has
	 * left none.
	 */
	private void closeAttempt(CheckoutAttempt attempt, CheckoutAttemptCloseReason reason, Counters counters) {
		if (!attempt.isOpen()) {
			return;
		}
		checkoutSlotClaimer.closeAttempt(attempt.getOrderId(), reason);
		counters.closed(1);
	}

	/** What one run did, in the two units the result reports. */
	private static final class Counters {

		private int captured;

		private int released;

		private int closed;

		private int held;

		private int failed;

		void captured() {
			captured++;
		}

		void released() {
			released++;
		}

		void closed(int rows) {
			closed += rows;
		}

		void held(int rows) {
			held += rows;
		}

		void failed(int rows) {
			failed += rows;
		}

		ReconciliationResult toResult() {
			return new ReconciliationResult(captured, released, closed, held, failed);
		}
	}
}