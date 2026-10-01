package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two ficha writes that must commit <em>before</em> the gateway is
 * called: its hold on one of the program's quota slots and the checkout price
 * snapshot. Nothing else.
 *
 * <p>This exists as its own bean for one reason that a private method could not
 * solve: {@code REQUIRES_NEW} works through the Spring proxy, so a
 * {@code @Transactional} method called from inside the same object would silently
 * join the caller's transaction instead of starting its own. The claim has to
 * commit <em>before</em> the gateway is called, and the release has to commit
 * <em>after</em> the caller's transaction has already rolled back — neither is
 * reachable without a separate proxied bean.
 *
 * <p>Three short transactions, and never a fourth that spans the network:
 * <ol>
 * <li>{@link #claim} — lock the config, count what is occupied by others, refuse
 * if the career is full, stamp the claim, commit. The lock is released here;</li>
 * <li>the caller calls Evo with no lock and no transaction held;</li>
 * <li>{@link #persistCheckoutSession} — record {@code orderId}/{@code sessionId}
 * so the confirmation can find the order again.</li>
 * </ol>
 *
 * <p>If Evo refuses the checkout, {@link #release} gives the slot straight back
 * rather than leaving a career one place short until its payment window closes,
 * for an order that does not exist.
 */
@Component
public class CheckoutSlotClaimer {

	private final AdmissionQuotaPort admissionQuotaPort;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final CandidateRepository candidateRepository;

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	/**
	 * Supplies "today" for the claim-expiry comparison, zone-pinned by
	 * {@code UseCaseConfig} for the same reason the checkout's window check is:
	 * a quota boundary decided from the server's default zone is one the
	 * applicant cannot see on any screen.
	 */
	private final Clock clock;

	/**
	 * Days a ficha may take to be paid, counting from the day it was registered.
	 * Needed here because a claim stops occupying a slot once its ficha's own
	 * window is over — otherwise a career would stay short for ten days waiting
	 * for somebody who is no longer allowed to pay.
	 */
	private final int paymentWindowDays;

	public CheckoutSlotClaimer(AdmissionQuotaPort admissionQuotaPort,
			AdmissionPaymentRepository admissionPaymentRepository, CandidateRepository candidateRepository,
			CheckoutAttemptRepository checkoutAttemptRepository, Clock clock,
			@Value("${sisa.admission.payment.deadline-days:10}") int paymentWindowDays) {
		this.admissionQuotaPort = admissionQuotaPort;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.candidateRepository = candidateRepository;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.clock = clock;
		this.paymentWindowDays = paymentWindowDays;
	}

	/**
	 * Takes one of the program's last slots for this candidate, or refuses.
	 *
	 * <p>Refusing here rather than at registration is the policy: a program may
	 * take far more registrations than it sells, and only these who get as far as
	 * asking to pay are competing for a place. It is also the last point at which
	 * refusing is free — once {@code INITIATE_CHECKOUT} has run, Evo holds an
	 * order, and telling the applicant no would mean a refund.
	 *
	 * <p>Concurrency: the lock on the config row is taken before the count and
	 * held until this transaction commits, so two candidates racing for the final
	 * slot are serialised. The second one to arrive re-reads the count and is
	 * refused. Nothing is oversold, and the loser loses a slot they never had.
	 *
	 * <p>The lock is only half of that, and the isolation level is the other half.
	 * Under MySQL's default {@code REPEATABLE READ}, the candidate lookup above runs
	 * first and fixes the transaction's read snapshot <em>before</em> the lock is
	 * taken. The count is a plain {@code SELECT}, so it then reads that stale
	 * snapshot rather than the row the winner just committed — and the second
	 * candidate counts zero occupied, sees a free slot, and claims. Both leave.
	 * {@code CheckoutSlotClaimerConcurrencyIT} reproduced exactly that: two
	 * candidates, one place, zero refusals. {@code READ_COMMITTED} makes the count
	 * a current read, which is what the lock needs it to be; correctness here comes
	 * from the lock, and the isolation level only has to stop hiding it.
	 *
	 * <p>Also stamps {@code amount} — the live tariff the caller resolved for
	 * this checkout — in the same transaction, so the price sent to Evo is
	 * committed before the order exists and the confirmation can later compare
	 * the bank's capture against a stored value. A refusal never reaches this
	 * write, because the capacity check runs first.
	 *
	 * @param amount the tariff quoted for this checkout, to store on the ficha
	 * @throws ProgramAdmissionConfigCapacityReachedException if the career is full
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public void claim(UUID candidateId, BigDecimal amount) {
		var candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		AdmissionQuotaPort.QuotaState quota = admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId());

		long occupiedByOthers = admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(
				candidate.getAdmissionConfigId(), candidateId, LocalDate.now(clock), paymentWindowDays);

		if (occupiedByOthers >= quota.maxCandidates()) {
			throw new ProgramAdmissionConfigCapacityReachedException(quotaReachedMessage());
		}

		AdmissionPayment payment = requirePendingPayment(candidateId);
		payment.reprice(amount);
		payment.claimCheckoutSlot();
		admissionPaymentRepository.save(payment);
	}

	/**
	 * Opens the {@link CheckoutAttempt} for an order that is about to be sent to
	 * EVO, and commits it <em>before</em> the caller makes that call (§3.6).
	 *
	 * <p>The ordering is the whole reason this method exists. The moment
	 * {@code INITIATE_CHECKOUT} runs, Evo may hold an order whose money is
	 * capturable at any second; a slot held with no recorded {@code orderId} can
	 * then only be released by guessing, which is what the old "release it after
	 * 30 minutes" rule was compensating for. Writing the attempt first closes
	 * that hole on its own — if the process dies at any step, the attempt is
	 * already in the database with the id the bank will answer to.
	 *
	 * <p>Separate from {@link #claim} rather than inside it because the two
	 * failures are different and both must be recoverable: a full career should
	 * not leave behind an attempt for an order that was never created, and the
	 * attempt must exist before any network call. Splitting them keeps each
	 * transaction to one reason.
	 *
	 * @param orderId     the id the order will be known by at EVO; generated by
	 *                    {@code OrderIdBuilder} with a random suffix, which is why
	 *                    attempts never overwrite each other
	 * @param candidateId whose ficha is being paid
	 * @param amount      the live tariff being charged, snapshotted so the sweep
	 *                    compares the capture against the number the applicant
	 *                    actually saw
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void openAttempt(String orderId, UUID candidateId, BigDecimal amount) {
		Instant now = Instant.now(clock);
		checkoutAttemptRepository.save(new CheckoutAttempt(orderId, candidateId, amount, now));
	}

	/**
	 * Closes an open attempt with the reason it ended.
	 *
	 * <p>Called by the caller on the two outcomes it can already tell: the
	 * browser reported a timeout or an error (§3.5), and EVO definitively refused
	 * to create the order at all. Everything else — a captured payment, a
	 * {@code FAILURE}, a process that died mid-call — belongs to the daily sweep,
	 * which asks the bank rather than inferring.
	 *
	 * <p>A missing attempt is not an error. By the time anything can report a
	 * timeout the row is committed, so absence means a caller with no attempt to
	 * close; there is nothing to reconcile and nothing to hold.
	 *
	 * @param orderId the attempt to settle; ignored when no such attempt exists
	 * @param reason  why it is over; must be a terminal reason
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void closeAttempt(String orderId, CheckoutAttemptCloseReason reason) {
		checkoutAttemptRepository.findByOrderId(orderId).ifPresent(attempt -> {
			attempt.close(reason, Instant.now(clock));
			checkoutAttemptRepository.save(attempt);
		});
	}

	/**
	 * Stores the gateway identifiers on the ficha so the confirmation flow can
	 * find the order it must verify.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void persistCheckoutSession(UUID candidateId, String orderId, String checkoutSessionId) {
		AdmissionPayment payment = requirePendingPayment(candidateId);
		payment.registerCheckout(orderId, checkoutSessionId);
		admissionPaymentRepository.save(payment);
	}

	/**
	 * Hands the slot back after the gateway refused the checkout.
	 *
	 * <p>Called only for a <em>definitive</em> refusal. A timeout or a dropped
	 * response is not one: Evo may have created the order anyway and captured the
	 * payment later, so releasing on an ambiguous failure would trade a stuck
	 * career for a possible oversell. Those go to reconciliation instead.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(UUID candidateId) {
		AdmissionPayment payment = requirePendingPayment(candidateId);
		payment.releaseCheckoutSlot();
		admissionPaymentRepository.save(payment);
	}

	/**
	 * A missing ficha here is not "no payment yet, nothing to do" — every
	 * candidate has one by the time they can reach checkout — so it is reported
	 * rather than silently skipped. A silent skip would leave the caller's
	 * release-then-retry loop with nothing to release and a claim held forever.
	 */
	private AdmissionPayment requirePendingPayment(UUID candidateId) {
		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));
		if (payment.getPaymentStatus() != AdmissionPaymentStatus.PENDING) {
			throw new IllegalStateException(
					"El pago " + candidateId + " no está PENDING, no se puede modificar la reserva de cupo");
		}
		return payment;
	}

	/**
	 * The only sentence a candidate ever reads for a full career, so it is built in
	 * one place and pinned by a test.
	 *
	 * <p>It says neither what is filling the quota nor how big the quota is. The first
	 * because the count includes in-progress checkouts, so claiming they were beaten by
	 * "paid" registrations would be wrong whenever the last slot is still settling —
	 * and it would turn a working rule into a race they lost. The second because a
	 * number invites the only follow-up question the system cannot answer: "so when
	 * does a place free up?" There is no waiting list to join — the quota is a cap, not
	 * a queue — so any figure next to it reads as a position in a line that does not
	 * exist. The cap is staff's business, and it moves when they edit
	 * {@code maxCandidates}.
	 *
	 * <p>It promises no retry either, for the same reason. Nothing schedules a
	 * release, so "try again later" would be a promise the code cannot keep.
	 *
	 * <p>No parameter: the sentence used to end in "de N fichas" and needed a
	 * singular/plural branch for a career capped at one. With the number gone that
	 * branch had nothing left to switch on, and the method is no longer a function of
	 * the quota at all.
	 */
	private static String quotaReachedMessage() {
		return "El cupo de esta carrera se agotó.";
	}
}
