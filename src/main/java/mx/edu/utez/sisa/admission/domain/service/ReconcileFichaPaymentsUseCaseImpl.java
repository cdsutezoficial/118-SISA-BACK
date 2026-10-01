package mx.edu.utez.sisa.admission.domain.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <li><b>every attempt refused</b> — nothing is in flight, so the place goes back, once,
 * and only if the claim is still the one this run decided about.</li>
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
	private Map<CheckoutAttempt, OrderSettlementDecider.Verdict> askBankAboutEveryAttempt(
			List<CheckoutAttempt> attempts) {
		Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts = new LinkedHashMap<>();
		for (CheckoutAttempt attempt : attempts) {
			verdicts.put(attempt,
					OrderSettlementDecider.decide(evoPaymentsGateway.retrieveOrder(attempt.getOrderId())));
		}
		return verdicts;
	}

	/**
	 * Phase 2: act on the fold.
	 *
	 * <p>The ficha is read once, here, and its claim travels with the decision. An
	 * already-paid ficha is settled on the answers alone — nothing about her place is
	 * touched, ever.
	 */
	private void settleFicha(UUID candidateId, List<CheckoutAttempt> attempts, Counters counters) {
		Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts = askBankAboutEveryAttempt(attempts);

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId).orElse(null);
		boolean alreadyPaid = payment != null && payment.getPaymentStatus() == AdmissionPaymentStatus.PAID;

		switch (OrderSettlementDecider.decideFicha(new ArrayList<>(verdicts.values()))) {
			case CAPTURED -> captureWon(candidateId, attempts, verdicts, counters, alreadyPaid);
			case RELEASEABLE -> releaseIsSafe(payment, alreadyPaid, attempts, counters);
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
	 */
	private void captureWon(UUID candidateId, List<CheckoutAttempt> attempts,
			Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts, Counters counters,
			boolean alreadyPaid) {
		CheckoutAttempt capturing = capturingAttempt(attempts, verdicts);
		boolean settledHere = false;

		if (!alreadyPaid && capturing != null) {
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
			}
		}

		for (CheckoutAttempt attempt : attempts) {
			OrderSettlementDecider.Verdict verdict = verdicts.get(attempt);
			if (verdict == OrderSettlementDecider.Verdict.CAPTURED) {
				if (!settledHere) {
					// Either the ficha was already paid, or no open row could carry the
					// confirmation. Nothing settles it this run, so the capturing row simply
					// records what the bank said — unless it is already closed, which
					// closeAttempt leaves alone.
					closeAttempt(attempt, CheckoutAttemptCloseReason.CAPTURED, counters);
				}
			} else if (verdict == OrderSettlementDecider.Verdict.RELEASEABLE) {
				closeAttempt(attempt, CheckoutAttemptCloseReason.REJECTED, counters);
			} else {
				// Still unanswered. It stays open, and the next run reads it as part of a
				// paid ficha, where closing it needs no further question.
				counters.held(1);
			}
		}
	}

	/**
	 * Every attempt of this ficha is refused, so nothing is in flight and the place goes
	 * back — once, and only if the claim is still the one this run read.
	 *
	 * <p>The compare-and-set matters because the writes are not in the same transaction
	 * as the reads: between asking the bank and writing, the applicant may have started
	 * another checkout, stamped a fresh claim and put a live order on it. A release that
	 * ignored the claim would give that one away.
	 *
	 * <p>A missing ficha row goes to the claimer, which reports it rather than skipping —
	 * that is its documented contract. The rows stay open so the next run can settle
	 * them once the ficha is readable again.
	 */
	private void releaseIsSafe(AdmissionPayment payment, boolean alreadyPaid, List<CheckoutAttempt> attempts,
			Counters counters) {
		if (alreadyPaid) {
			closeAll(attempts, CheckoutAttemptCloseReason.REJECTED, counters);
			return;
		}
		UUID candidateId = attempts.get(0).getCandidateId();

		if (payment == null) {
			// Handing it to the claimer rather than skipping: a ficha with no readable
			// payment row is that component's documented error, and a quiet skip here
			// would leave the claim held with nothing left to ask about it.
			checkoutSlotClaimer.release(candidateId, null);
			return;
		}

		if (checkoutSlotClaimer.release(candidateId, payment.getCheckoutClaimedAt())) {
			counters.released();
		} else {
			// The claim moved while the bank was being asked: somebody is mid-checkout on
			// this very ficha, so the place stays held — by them, this time.
			log.info("El cupo de la ficha {} cambió mientras se conciliaba; no se liberó", candidateId);
		}

		// The rows close either way. The refusal is what the bank said about each of them
		// and that does not stop being true because somebody else re-stamped the claim.
		closeAll(attempts, CheckoutAttemptCloseReason.REJECTED, counters);
	}

	/**
	 * The attempt to hand to the confirmation: one the bank says captured, and one nobody
	 * has settled yet.
	 *
	 * <p>Both halves are load-bearing. Without the first, an already-paid ficha would be
	 * re-confirmed. Without the second, a capture whose row the browser closed a moment
	 * ago would be thrown at a confirmation that only accepts an open attempt, and the
	 * run would log a failure for a payment that had already been settled correctly.
	 */
	private CheckoutAttempt capturingAttempt(List<CheckoutAttempt> attempts,
			Map<CheckoutAttempt, OrderSettlementDecider.Verdict> verdicts) {
		return attempts.stream().filter(attempt -> verdicts.get(attempt) == OrderSettlementDecider.Verdict.CAPTURED)
				.filter(CheckoutAttempt::isOpen).findFirst().orElse(null);
	}

	private void closeAll(List<CheckoutAttempt> attempts, CheckoutAttemptCloseReason reason, Counters counters) {
		for (CheckoutAttempt attempt : attempts) {
			closeAttempt(attempt, reason, counters);
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