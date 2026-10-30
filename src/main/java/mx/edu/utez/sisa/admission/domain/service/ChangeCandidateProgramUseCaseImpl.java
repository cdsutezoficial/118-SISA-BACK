package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeCandidateProgramUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateProgramChangeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateProgramChangeNotAllowedException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

public class ChangeCandidateProgramUseCaseImpl implements ChangeCandidateProgramUseCase {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final CandidateRepository candidateRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final FichaPaymentPort fichaPaymentPort;

	private final AdmissionQuotaPort admissionQuotaPort;

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	private final CandidateProgramChangeRepository programChangeRepository;

	private final Clock clock;

	private final int fichaDeadlineDays;

	private final int checkoutHoldMinutes;

	public ChangeCandidateProgramUseCaseImpl(CandidateRepository candidateRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, FichaAmountResolver fichaAmountResolver,
			FichaPaymentPort fichaPaymentPort, AdmissionQuotaPort admissionQuotaPort,
			CheckoutAttemptRepository checkoutAttemptRepository,
			CandidateProgramChangeRepository programChangeRepository, Clock clock, int fichaDeadlineDays,
			int checkoutHoldMinutes) {
		this.candidateRepository = candidateRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.fichaPaymentPort = fichaPaymentPort;
		this.admissionQuotaPort = admissionQuotaPort;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.programChangeRepository = programChangeRepository;
		this.clock = clock;
		this.fichaDeadlineDays = fichaDeadlineDays;
		this.checkoutHoldMinutes = checkoutHoldMinutes;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isAllowed(UUID candidateId) {
		return blockedReason(candidateId).isEmpty();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> blockedReason(UUID candidateId) {
		Candidate candidate = requireCandidate(candidateId);
		try {
			requireAllowed(candidate);
			return Optional.empty();
		} catch (CandidateProgramChangeNotAllowedException ex) {
			return Optional.of(ex.getMessage());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public ProgramChangePreview preview(UUID candidateId, UUID admissionConfigId) {
		Candidate candidate = requireCandidate(candidateId);
		FichaPaymentPort.FichaCharge charge = requireAllowed(candidate);
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo from = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId()).orElse(null);
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo to = requireConfig(admissionConfigId);
		if (to.id().equals(candidate.getAdmissionConfigId())) {
			throw new InvalidCandidateFichaDataException("El candidato ya está en esa carrera.");
		}
		PaymentConceptQueryPort.FichaConcept concept = fichaAmountResolver.resolveConcept(to.programId(),
				LocalDate.now(clock));
		LocalDate today = LocalDate.now(clock);

		if (!charge.isPaid()) {
			FichaPaymentPort.FichaQuote next = fichaPaymentPort.quoteForProgram(charge.financePaymentId(),
					concept.code(), to.programId());
			LocalDate deadline = FichaPaymentWindow.deadlineOf(candidate,
					to.closesAt().atZone(clock.getZone()).toLocalDate(), fichaDeadlineDays, clock.getZone());
			return new ProgramChangePreview(candidate.getId(), candidate.getAdmissionConfigId(), programName(from),
					to.id(), to.programName(), Mode.UNPAID, currentAmount(charge.financePaymentId()), ZERO, ZERO,
					next.originalAmount(), next.discountAmount(), next.amountDue(), next.amountDue(), ZERO, true,
					deadline, today.isAfter(deadline));
		}

		UUID fromPaymentId = candidate.collectingPaymentId();
		FichaPaymentPort.ProgramChangeAmounts amounts = fichaPaymentPort.previewRebill(fromPaymentId,
				concept.code(), to.programId());
		long occupied = admissionQuotaPort.countOccupiedExcluding(to.id(), candidate.getId(),
				FichaPaymentWindow.attemptsHoldingSince(Instant.now(clock), checkoutHoldMinutes));
		return new ProgramChangePreview(candidate.getId(), candidate.getAdmissionConfigId(), programName(from),
				to.id(), to.programName(), Mode.PAID, currentAmount(fromPaymentId), amounts.alreadyPaid(),
				amounts.otherCredit(), amounts.newAmount(), ZERO, amounts.newAmount(), amounts.toPay(),
				amounts.creditLeft(), occupied < to.maxCandidates(),
				amounts.settled() ? null : differenceDeadline(today), false);
	}

	@Override
	@Transactional
	public void change(UUID candidateId, UUID admissionConfigId, UUID actingUserId) {
		Candidate candidate = requireCandidate(candidateId);
		if (admissionConfigId == null || admissionConfigId.equals(candidate.getAdmissionConfigId())) {
			return;
		}
		FichaPaymentPort.FichaCharge charge = requireAllowed(candidate);
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo to = requireConfig(admissionConfigId);
		PaymentConceptQueryPort.FichaConcept concept = fichaAmountResolver.resolveConcept(to.programId(),
				LocalDate.now(clock));
		UUID fromConfigId = candidate.getAdmissionConfigId();
		Instant now = Instant.now(clock);

		if (!charge.isPaid()) {
			BigDecimal previous = currentAmount(charge.financePaymentId());
			fichaPaymentPort.reassignProgram(charge.financePaymentId(), concept.code(), to.programId());
			candidate.changeAdmissionConfig(to.id());
			candidateRepository.save(candidate);
			BigDecimal newAmount = currentAmount(charge.financePaymentId());
			programChangeRepository.save(new CandidateProgramChange(candidate.getId(), fromConfigId, to.id(), false,
					charge.financePaymentId(), null, previous, ZERO, newAmount, newAmount, ZERO, actingUserId, now));
			return;
		}

		// Ya ocupa un lugar: la carrera nueva tiene que tenerlo. Mismo candado y
		// misma cuenta que el checkout, para no competir con un pago en curso.
		AdmissionQuotaPort.QuotaState quota = admissionQuotaPort.lockQuota(to.id());
		long occupied = admissionQuotaPort.countOccupiedExcluding(to.id(), candidate.getId(),
				FichaPaymentWindow.attemptsHoldingSince(now, checkoutHoldMinutes));
		if (occupied >= quota.maxCandidates()) {
			throw new ProgramAdmissionConfigCapacityReachedException("El cupo de esta carrera se agotó.");
		}

		UUID fromPaymentId = candidate.collectingPaymentId();
		BigDecimal previous = currentAmount(fromPaymentId);
		long changes = programChangeRepository.countByCandidateId(candidate.getId());
		Instant dueAt = differenceDeadline(LocalDate.now(clock)).atTime(LocalTime.MAX).atZone(clock.getZone())
				.toInstant();
		FichaPaymentPort.ProgramChangeAmounts amounts = fichaPaymentPort.rebill(
				new FichaPaymentPort.RebillFichaCommand(fromPaymentId, concept.code(), to.programId(), dueAt,
						charge.referenceNumber() + "-C" + (changes + 1), actingUserId));
		if (amounts.settled()) {
			candidate.adoptFichaPayment(amounts.financePaymentId());
		} else {
			candidate.awaitProgramChangePayment(amounts.financePaymentId());
		}
		candidate.changeAdmissionConfig(to.id());
		candidateRepository.save(candidate);
		programChangeRepository.save(new CandidateProgramChange(candidate.getId(), fromConfigId, to.id(), true,
				fromPaymentId, amounts.financePaymentId(), previous, amounts.alreadyPaid(), amounts.newAmount(),
				amounts.toPay(), amounts.creditLeft(), actingUserId, now));
	}

	/**
	 * El cambio no se permite con el candidato inscrito (lo anterior queda como
	 * historial y lo resuelve Inscripciones), con la ficha vencida, ni con dinero
	 * en vuelo: un intento de pago abierto podría capturarse contra el cargo que
	 * se está moviendo.
	 */
	private FichaPaymentPort.FichaCharge requireAllowed(Candidate candidate) {
		if (candidate.getStatus() == CandidateStatus.ENROLLED) {
			throw new CandidateProgramChangeNotAllowedException(
					"El candidato ya está inscrito: la carrera ya no se cambia desde la ficha.");
		}
		FichaPaymentPort.FichaCharge charge = FichaCharges.of(candidate, fichaPaymentPort).orElse(null);
		if (candidate.getStatus() == CandidateStatus.PAYMENT_EXPIRED || charge == null || charge.cancelled()) {
			throw new CandidateProgramChangeNotAllowedException(
					"La ficha venció sin pago: la carrera ya no se puede cambiar.");
		}
		if (checkoutInProgress(candidate, charge)) {
			throw new CandidateProgramChangeNotAllowedException(
					"Hay un pago en línea en curso para esta ficha. Espera a que se confirme o se libere para cambiar la carrera.");
		}
		return charge;
	}

	/**
	 * Un pago en línea sigue en curso si hay algo por cobrar (la ficha sin pagar o
	 * una diferencia pendiente) y un intento abierto dentro de la retención del
	 * checkout. Un intento que quedó {@code STARTED} porque se cerró el panel sin
	 * pagar ya no cuenta pasada la retención, igual que en el cupo; y con todo
	 * pagado ningún intento puede cobrar nada más.
	 */
	private boolean checkoutInProgress(Candidate candidate, FichaPaymentPort.FichaCharge charge) {
		boolean somethingToCollect = candidate.getProgramChangePaymentId() != null || !charge.isPaid();
		if (!somethingToCollect) {
			return false;
		}
		Instant holdingSince = FichaPaymentWindow.attemptsHoldingSince(Instant.now(clock), checkoutHoldMinutes);
		return checkoutAttemptRepository.findOpenAttemptsByCandidateId(candidate.getId()).stream()
				.anyMatch(attempt -> attempt.getCreatedAt() != null && attempt.getCreatedAt().isAfter(holdingSince));
	}

	/** Lo que el cargo cuesta hoy para él; {@code null} si su carrera ya no tiene tarifa. */
	private BigDecimal currentAmount(UUID financePaymentId) {
		try {
			return fichaPaymentPort.quote(financePaymentId).amountDue();
		} catch (FichaPaymentConceptNotFoundException ex) {
			return null;
		}
	}

	/** La diferencia se paga con el mismo plazo de la ficha, contado desde el cambio. */
	private LocalDate differenceDeadline(LocalDate changedOn) {
		return changedOn.plusDays(fichaDeadlineDays);
	}

	private Candidate requireCandidate(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
	}

	private ProgramAdmissionConfigQueryPort.AdmissionConfigInfo requireConfig(UUID admissionConfigId) {
		if (admissionConfigId == null) {
			throw new InvalidCandidateFichaDataException("La carrera es obligatoria.");
		}
		return programAdmissionConfigQueryPort.findById(admissionConfigId)
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + admissionConfigId));
	}

	private static String programName(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config) {
		return config == null ? null : config.programName();
	}
}
