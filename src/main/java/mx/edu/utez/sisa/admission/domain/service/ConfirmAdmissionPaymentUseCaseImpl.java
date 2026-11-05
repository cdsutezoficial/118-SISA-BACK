package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateNotificationPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.CashAmountChangedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Payment-confirmation interactor (design:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md},
 * {@code ConfirmAdmissionPaymentUseCase}): marks the ticket payment
 * {@code PAID} and the candidate {@code PAID} in one transaction, then returns
 * the data the web layer needs to render the paid ficha / confirmation email.
 *
 * <p>Validations, in order:
 * <ol>
 * <li>the {@code candidateId} must resolve to a {@code Candidate} and its
 * {@code ADMISSION_FICHA} payment must exist (404,
 * {@code CandidateNotFoundException});</li>
 * <li>the payment must still be {@code PENDING} (409,
 * {@code CandidateAlreadyPaidException}) — an already-paid ficha is a
 * repeat-confirmation, not a new transition.</li>
 * </ol>
 *
 * <p>The receipt number ({@code REC-{yyyyMMdd}-{folioSeq}}) is generated here.
 * It deliberately does <em>not</em> follow the payment reference: that one is
 * {@code REF-{folio}} (decisión 11.1), because a reference has to be recomputable
 * from the ficha alone, while a receipt is minted once at payment time and
 * stamped on an email and a PDF. It still reads {@code LocalDate.now()} from the
 * server's default zone, which the reference no longer does — not worth a format
 * change on strings already printed and mailed, but not something to copy
 * anywhere new. Email dispatch is the web layer's concern (best-effort
 * notification, never part of this transaction).
 */
public class ConfirmAdmissionPaymentUseCaseImpl implements ConfirmAdmissionPaymentUseCase {

	/** Who collected the money, as the receipt shows it: the online checkout or the system itself. */
	private static final String ONLINE_COLLECTOR = "Pago en línea";

	private static final String SYSTEM_COLLECTOR = "Sistema";

	private final CandidateRepository candidateRepository;


	private final FichaPaymentPort fichaPaymentPort;

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	private final CandidateNotificationPort notifications;

	/** F1: the place a cash payment takes is checked under the same lock as the checkout's. */
	private final AdmissionQuotaPort admissionQuotaPort;

	private final Clock clock;

	private final int checkoutHoldMinutes;

	public ConfirmAdmissionPaymentUseCaseImpl(CandidateRepository candidateRepository,
			FichaPaymentPort fichaPaymentPort,
			CheckoutAttemptRepository checkoutAttemptRepository, CandidateNotificationPort notifications,
			AdmissionQuotaPort admissionQuotaPort, Clock clock, int checkoutHoldMinutes) {
		this.notifications = notifications;
		this.admissionQuotaPort = admissionQuotaPort;
		this.clock = clock;
		this.checkoutHoldMinutes = checkoutHoldMinutes;
		this.candidateRepository = candidateRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
	}

	@Override
	@Transactional
	public ConfirmPaymentResult confirm(UUID candidateId, String capturedOrderId) {
		Candidate candidate = requireCandidate(candidateId);
		FichaPaymentPort.FichaCharge charge = requireOwed(candidate);

		// The numbers booked are the captured attempt's, when there is one.
		CheckoutAttempt attempt = capturedOrderId == null ? null
				: checkoutAttemptRepository.findByOrderId(capturedOrderId).orElse(null);
		ConfirmPaymentResult result = settle(candidate, charge, chargedBy(attempt), attempt != null, capturedOrderId,
				null, attempt != null ? ONLINE_COLLECTOR : SYSTEM_COLLECTOR);
		// F3: here, not in the controller, so the reconciliation's confirmations send a
		// receipt too. Delivered after this transaction commits.
		notifications.paymentConfirmed(result);
		return result;
	}

	/**
	 * {@code READ_COMMITTED} for the same reason as {@code CheckoutSlotClaimer#claim}: the
	 * count taken under the lock has to see places committed a moment ago.
	 */
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ConfirmPaymentResult confirmCash(UUID candidateId, BigDecimal expectedAmount, UUID collectorId,
			String collectorName) {
		Candidate candidate = requireCandidate(candidateId);
		FichaPaymentPort.FichaCharge charge = requireOwed(candidate);
		// A difference does not compete for a place: a paid candidate already holds one.
		if (candidate.getProgramChangePaymentId() == null) {
			requirePlace(candidate);
		}
		ConfirmPaymentResult result = settle(candidate, charge, null, false, null, collectorId, collectorName);
		if (expectedAmount == null || result.amount().compareTo(expectedAmount) != 0) {
			// Rolls the whole booking back: the cashier collects what the ficha owes now.
			throw new CashAmountChangedException("El monto a cobrar cambió: ahora son $" + result.amount()
					+ ". Vuelve a consultar la ficha antes de cobrar.");
		}
		notifications.paymentConfirmed(result);
		return result;
	}

	private Candidate requireCandidate(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
	}

	/** The difference a career change left to pay, when there is one; the ficha otherwise. */
	private FichaPaymentPort.FichaCharge requireOwed(Candidate candidate) {
		FichaPaymentPort.FichaCharge charge = FichaCharges.collecting(candidate, fichaPaymentPort)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidate.getId()));
		if (charge.isPaid()) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}
		return charge;
	}

	/** Same count as the checkout's claim: paid fichas plus open attempts still holding a place. */
	private void requirePlace(Candidate candidate) {
		AdmissionQuotaPort.QuotaState quota = admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId());
		long occupiedByOthers = admissionQuotaPort.countOccupiedExcluding(candidate.getAdmissionConfigId(),
				candidate.getId(), FichaPaymentWindow.attemptsHoldingSince(Instant.now(clock), checkoutHoldMinutes));
		if (occupiedByOthers >= quota.maxCandidates()) {
			throw new ProgramAdmissionConfigCapacityReachedException("El cupo de esta carrera se agotó.");
		}
	}

	/**
	 * Same transaction: the finance charge and the candidate are settled together, or
	 * neither is. The receipt goes on the abono.
	 */
	private ConfirmPaymentResult settle(Candidate candidate, FichaPaymentPort.FichaCharge charge,
			FichaPaymentPort.ChargedAmounts charged, boolean online, String orderId, UUID collectorId,
			String collectorName) {
		boolean programChange = candidate.getProgramChangePaymentId() != null;
		String receipt = generateReceipt(candidate.getFolio()) + (programChange ? "-C" : "");
		FichaPaymentPort.SettledFicha settled = fichaPaymentPort.settle(new FichaPaymentPort.SettleFichaCommand(
				charge.financePaymentId(), charged, online, orderId, receipt, charge.referenceNumber(), collectorId,
				collectorName));

		// E6: the earlier charge the carried money came from, read before the
		// difference replaces it as the ficha.
		FichaPaymentPort.FichaCharge previousCharge = programChange && candidate.getFichaPaymentId() != null
				? fichaPaymentPort.findCharge(candidate.getFichaPaymentId()).orElse(null)
				: null;

		if (programChange) {
			// Paid in full: the new career's charge is the ficha from now on.
			candidate.adoptFichaPayment(charge.financePaymentId());
		} else {
			candidate.markPaid();
		}
		candidateRepository.save(candidate);

		FichaPaymentPort.FichaCharge paid = fichaPaymentPort.findCharge(charge.financePaymentId()).orElse(charge);
		// E6: `settled.amountPaid()` is everything the charge has received. What this
		// operation paid is the part the charge did not have before it.
		BigDecimal before = charge.amountPaid() == null ? BigDecimal.ZERO : charge.amountPaid();
		BigDecimal total = settled.amountPaid();
		BigDecimal paidNow = total.subtract(before).max(BigDecimal.ZERO);
		ConfirmPaymentResult result = new ConfirmPaymentResult(candidate.getId(), candidate.getFolio(),
				candidate.getStatus(), charge.referenceNumber(), paidNow, paid.paidAt() != null ? paid.paidAt() : Instant.now(),
				paid.receiptNumber() != null ? paid.receiptNumber() : receipt, total, before,
				previousCharge == null ? null : previousCharge.receiptNumber(),
				previousCharge == null ? null : previousCharge.paidAt(), programChange);
		return result;
	}

	/**
	 * The attempt's snapshot, or {@code null} when it has none (no attempt, or
	 * one opened before snapshots existed) — finance then prices live.
	 */
	private static FichaPaymentPort.ChargedAmounts chargedBy(CheckoutAttempt attempt) {
		if (attempt == null || !attempt.hasSnapshot()) {
			return null;
		}
		return new FichaPaymentPort.ChargedAmounts(attempt.getRateId(), attempt.getOriginalAmount(),
				attempt.getDiscountAmount(), attempt.getAmount(), attempt.getBenefitId());
	}

	/** {@code REC-{yyyyMMdd}-{folioSeq}} — minted at payment time; see the class javadoc. */
	private static String generateReceipt(String folio) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		return String.format("REC-%s-%s", today, seq);
	}
}