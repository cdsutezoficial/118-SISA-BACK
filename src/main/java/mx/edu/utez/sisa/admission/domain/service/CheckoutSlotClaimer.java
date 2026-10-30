package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short, committed writes around the EVO call of a ficha checkout.
 *
 * <p>Since 2026-10-23 the ficha has no table of its own: a quota place is held
 * by the <b>checkout attempt</b> itself (finance's {@code checkout_attempt},
 * keyed by the ficha's charge). An attempt still open and younger than
 * the checkout hold ({@code sisa.admission.payment.checkout-hold-minutes}) occupies a place; a settled charge
 * occupies one for good. So "claiming" a place <em>is</em> opening the
 * attempt, and "releasing" it is closing the attempt.
 *
 * <p>Every method runs in its own transaction ({@code REQUIRES_NEW}) because
 * the caller is deliberately not transactional: it spans a third-party call,
 * and nothing must be locked while EVO is being contacted.
 */
@Component
public class CheckoutSlotClaimer {

	/** Who "collected" a ficha a FULL discount settled at $0, as the receipt shows it. */
	private static final String EXEMPTION_COLLECTOR = "Descuento 100%";

	private final AdmissionQuotaPort admissionQuotaPort;
	private final CandidateRepository candidateRepository;
	private final CheckoutAttemptRepository checkoutAttemptRepository;
	private final FichaPaymentPort fichaPaymentPort;
	private final Clock clock;

	/** Minutes an open checkout attempt holds a quota place. */
	private final int checkoutHoldMinutes;

	public CheckoutSlotClaimer(AdmissionQuotaPort admissionQuotaPort, CandidateRepository candidateRepository,
			CheckoutAttemptRepository checkoutAttemptRepository, FichaPaymentPort fichaPaymentPort, Clock clock,
			@Value("${sisa.admission.payment.checkout-hold-minutes:15}") int checkoutHoldMinutes) {
		this.admissionQuotaPort = admissionQuotaPort;
		this.candidateRepository = candidateRepository;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.clock = clock;
		this.checkoutHoldMinutes = checkoutHoldMinutes;
	}

	/**
	 * Takes one of the program's places for this candidate by opening the
	 * checkout attempt that will be sent to EVO, or refuses.
	 *
	 * <p>Refusing here rather than at registration is the policy: a program may
	 * take far more registrations than it sells, and only those who get as far
	 * as paying compete for a place. It is also the last point at which refusing
	 * is free — once the order exists at EVO, saying no would mean a refund.
	 *
	 * <p>The attempt is committed <em>before</em> the caller contacts EVO, so a
	 * held place always has an {@code orderId} the bank can be asked about. A
	 * full career leaves no attempt behind: the count is checked first.
	 *
	 * <p>Concurrency: the lock on the config row is taken before the count and
	 * held until commit, so two candidates racing for the final place are
	 * serialised. {@code READ_COMMITTED} makes the count a current read (under
	 * MySQL's {@code REPEATABLE READ} the snapshot fixed by the candidate lookup
	 * would hide the winner's committed attempt and both would pass).
	 *
	 * <p>The candidate's own attempts are left out of the count, so a retry of
	 * their own checkout never competes with the place they already hold.
	 *
	 * @param orderId the id the order will carry at EVO
	 * @param price   the quote frozen for this checkout; the attempt keeps its
	 *                own snapshot so whichever attempt captures is booked with
	 *                the numbers it charged
	 * @throws ProgramAdmissionConfigCapacityReachedException if the career is full
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public void claim(UUID candidateId, String orderId, FichaPaymentPort.FichaQuote price) {
		Candidate candidate = requireCandidate(candidateId);
		AdmissionQuotaPort.QuotaState quota = admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId());
		Instant now = Instant.now(clock);
		long occupiedByOthers = admissionQuotaPort.countOccupiedExcluding(candidate.getAdmissionConfigId(),
				candidateId, FichaPaymentWindow.attemptsHoldingSince(now, checkoutHoldMinutes));
		if (occupiedByOthers >= quota.maxCandidates()) {
			throw new ProgramAdmissionConfigCapacityReachedException(quotaReachedMessage());
		}
		requirePendingCharge(candidate);
		checkoutAttemptRepository.save(new CheckoutAttempt(orderId, price.financePaymentId(), candidateId, null,
				price.amountDue(), now, price.rateId(), price.originalAmount(), price.discountAmount(),
				price.benefitId()));
	}

	/**
	 * Opens the checkout attempt for the difference a career change left to pay.
	 * No quota check: a paid candidate already holds a place, and the change
	 * itself checked the new career had one. The attempt carries no price
	 * snapshot — the charge's price is fixed, so it is settled with its own
	 * numbers.
	 *
	 * @param amount what EVO is asked for: what is left to pay
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void claimProgramChange(UUID candidateId, String orderId, UUID paymentId, BigDecimal amount) {
		requireCandidate(candidateId);
		checkoutAttemptRepository.save(new CheckoutAttempt(orderId, paymentId, candidateId, null, amount,
				Instant.now(clock), null, null, null, null));
	}

	/**
	 * Settles the ficha at {@code $0} because a {@code FULL} discount covers the
	 * whole tariff: the exemption is booked on the finance charge (its $0 entry
	 * carries the receipt) and the candidate becomes {@code PAID}, so the ficha
	 * behaves like any other paid one downstream (quota, induction).
	 *
	 * @param price the frozen quote, whose {@code amountDue} is {@code $0}
	 * @return the receipt number minted for the exemption
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public String settleExempt(UUID candidateId, FichaPaymentPort.FichaQuote price) {
		Candidate candidate = requireCandidate(candidateId);
		FichaPaymentPort.FichaCharge charge = requirePendingCharge(candidate);
		String receipt = exemptionReceipt(candidate.getFolio());
		fichaPaymentPort.settle(new FichaPaymentPort.SettleFichaCommand(price.financePaymentId(), price.charged(),
				false, null, receipt, charge.referenceNumber(), null, EXEMPTION_COLLECTOR));
		candidate.markPaid();
		candidateRepository.save(candidate);
		return receipt;
	}

	/**
	 * {@code EXE-{yyyyMMdd}-{folioSeq}} — the discount-backed "receipt", distinct
	 * in prefix from the {@code REC-} a bank payment mints.
	 */
	private String exemptionReceipt(String folio) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String today = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
		return String.format("EXE-%s-%s", today, seq);
	}

	/**
	 * Closes an open attempt with the reason it ended — which also hands its
	 * place back, since only open attempts hold one. A missing or already
	 * closed attempt is left alone.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void closeAttempt(String orderId, CheckoutAttemptCloseReason reason) {
		checkoutAttemptRepository.findByOrderId(orderId).ifPresent(attempt -> {
			if (!attempt.isOpen()) {
				return;
			}
			attempt.close(reason, Instant.now(clock));
			checkoutAttemptRepository.save(attempt);
		});
	}

	/** Stores the hosted-checkout session EVO opened on the attempt. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void persistCheckoutSession(String orderId, String checkoutSessionId) {
		checkoutAttemptRepository.findByOrderId(orderId).ifPresent(attempt -> {
			attempt.registerSession(checkoutSessionId);
			checkoutAttemptRepository.save(attempt);
		});
	}

	private Candidate requireCandidate(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
	}

	/**
	 * Every candidate has a ficha charge by the time they can reach checkout, so
	 * a missing one is reported rather than skipped.
	 */
	private FichaPaymentPort.FichaCharge requirePendingCharge(Candidate candidate) {
		FichaPaymentPort.FichaCharge charge = FichaCharges.of(candidate, fichaPaymentPort)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidate.getId()));
		if (charge.isPaid() || charge.cancelled()) {
			throw new IllegalStateException("La ficha " + candidate.getFolio()
					+ " no está pendiente de pago; no se puede iniciar el cobro.");
		}
		return charge;
	}

	/**
	 * The only sentence a candidate ever reads for a full career. It says neither
	 * what fills the quota (in-progress checkouts count) nor its size (there is
	 * no waiting list to join), and promises no retry.
	 */
	private static String quotaReachedMessage() {
		return "El cupo de esta carrera se agotó.";
	}
}
