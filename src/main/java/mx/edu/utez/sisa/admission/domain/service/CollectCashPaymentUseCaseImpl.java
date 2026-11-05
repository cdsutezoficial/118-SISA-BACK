package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.CollectCashPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.StaffDirectoryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.NothingToCollectException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * F1: cash at the cash desk, through admission so the charge and the candidate are
 * settled together. Booking an abono straight in {@code finance} would leave the charge
 * paid and the candidate {@code REGISTERED}.
 *
 * <p>Every refusal happens before the money changes hands, in this order:
 * <ol>
 * <li>open online sessions are settled with the bank first; one that can still capture
 * refuses ({@code PaymentInProgressException}), one that already captured leaves the
 * ficha paid ({@code CandidateAlreadyPaidException});</li>
 * <li>the ficha is still owed and inside its window (the same {@link FichaPaymentGate}
 * the online checkout uses), or the difference is inside its deadline;</li>
 * <li>a 100 % discount leaves nothing to take ({@code NothingToCollectException});</li>
 * <li>the career still has a place. Checked here without a lock so the cashier is told
 * before taking the money, and again under the lock when booking.</li>
 * </ol>
 *
 * <p>Not transactional: the bank is asked first, and no transaction is held across that
 * call. The booking is {@link ConfirmAdmissionPaymentUseCase#confirmCash}, in its own.
 */
public class CollectCashPaymentUseCaseImpl implements CollectCashPaymentUseCase {

	private final CandidateRepository candidateRepository;

	private final FichaPaymentPort fichaPaymentPort;

	private final FichaPaymentGate fichaPaymentGate;

	private final PendingCheckoutGuard pendingCheckoutGuard;

	private final AdmissionQuotaPort admissionQuotaPort;

	private final ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	private final StaffDirectoryPort staffDirectory;

	private final Clock clock;

	private final int checkoutHoldMinutes;

	public CollectCashPaymentUseCaseImpl(CandidateRepository candidateRepository, FichaPaymentPort fichaPaymentPort,
			FichaPaymentGate fichaPaymentGate, PendingCheckoutGuard pendingCheckoutGuard,
			AdmissionQuotaPort admissionQuotaPort, ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase,
			StaffDirectoryPort staffDirectory, Clock clock, int checkoutHoldMinutes) {
		this.candidateRepository = candidateRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.fichaPaymentGate = fichaPaymentGate;
		this.pendingCheckoutGuard = pendingCheckoutGuard;
		this.admissionQuotaPort = admissionQuotaPort;
		this.confirmAdmissionPaymentUseCase = confirmAdmissionPaymentUseCase;
		this.staffDirectory = staffDirectory;
		this.clock = clock;
		this.checkoutHoldMinutes = checkoutHoldMinutes;
	}

	@Override
	public CashPaymentPreview preview(UUID candidateId) {
		CashPaymentPreview due = due(settledCandidate(candidateId));
		// The saldo a favor the cashier can use first; it is applied, if chosen, when collecting.
		BigDecimal credit = fichaPaymentPort.availableCredit(candidateId);
		return new CashPaymentPreview(due.candidateId(), due.folio(), due.referenceNumber(), due.programChange(),
				due.total(), due.alreadyPaid(), due.toCollect(), due.dueAt(),
				credit == null ? BigDecimal.ZERO : credit.max(BigDecimal.ZERO));
	}

	@Override
	public ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult collect(UUID candidateId, BigDecimal expectedAmount,
			boolean useCredit, UUID collectorId) {
		due(settledCandidate(candidateId));
		return confirmAdmissionPaymentUseCase.confirmCash(candidateId, expectedAmount, useCredit, collectorId,
				staffDirectory.displayName(collectorId));
	}

	/** The candidate as it stands once its open online sessions are settled with the bank. */
	private Candidate settledCandidate(UUID candidateId) {
		pendingCheckoutGuard.settleBeforeCashPayment(requireCandidate(candidateId));
		// A capture the guard applied changed the candidate: read it again.
		return requireCandidate(candidateId);
	}

	private CashPaymentPreview due(Candidate candidate) {
		if (candidate.getProgramChangePaymentId() != null) {
			return differenceDue(candidate);
		}
		FichaPaymentPort.FichaCharge charge = FichaCharges.of(candidate, fichaPaymentPort)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidate.getId()));
		if (charge.isPaid()) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}
		if (charge.cancelled()) {
			throw new FichaPaymentExpiredException("La ficha " + candidate.getFolio()
					+ " venció sin pagarse. Ya no se puede cobrar.");
		}
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = fichaPaymentGate.requireOpen(candidate);

		FichaPaymentPort.FichaQuote quote = fichaPaymentPort.quote(charge.financePaymentId());
		BigDecimal paidSoFar = charge.amountPaid() == null ? BigDecimal.ZERO : charge.amountPaid();
		BigDecimal toCollect = quote.amountDue().subtract(paidSoFar).max(BigDecimal.ZERO);
		if (toCollect.signum() == 0) {
			throw new NothingToCollectException("La ficha " + candidate.getFolio()
					+ " no tiene nada que cobrar: un descuento la cubre completa. Se liquida desde el portal.");
		}

		long occupiedByOthers = admissionQuotaPort.countOccupiedExcluding(config.id(), candidate.getId(),
				FichaPaymentWindow.attemptsHoldingSince(Instant.now(clock), checkoutHoldMinutes));
		if (occupiedByOthers >= config.maxCandidates()) {
			throw new ProgramAdmissionConfigCapacityReachedException(
					"El cupo de esta carrera se agotó. No recibas el pago.");
		}
		if (candidateRepository.existsAnotherValidInPeriod(candidate.getCurp(), config.periodId(),
				candidate.getId())) {
			throw new CandidateAlreadyExistsException("Este CURP ya tiene una candidatura válida en este proceso "
					+ "de admisión. No recibas el pago.");
		}
		return new CashPaymentPreview(candidate.getId(), candidate.getFolio(), charge.referenceNumber(), false,
				quote.amountDue(), paidSoFar, toCollect, charge.dueAt());
	}

	private CashPaymentPreview differenceDue(Candidate candidate) {
		FichaPaymentPort.ProgramChangeCharge charge = fichaPaymentPort
				.findProgramChangeCharge(candidate.getProgramChangePaymentId())
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene diferencia de pago: " + candidate.getId()));
		if (charge.paid() || charge.toPay().signum() == 0) {
			throw new CandidateAlreadyPaidException(
					"La diferencia del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}
		if (charge.dueAt() != null && Instant.now(clock).isAfter(charge.dueAt())) {
			throw new FichaPaymentExpiredException(
					"El plazo para pagar la diferencia por cambio de carrera venció. Comunícate con Servicios Escolares.");
		}
		return new CashPaymentPreview(candidate.getId(), candidate.getFolio(), charge.referenceNumber(), true,
				charge.newAmount(), charge.newAmount().subtract(charge.toPay()), charge.toPay(), charge.dueAt());
	}

	private Candidate requireCandidate(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
	}
}
