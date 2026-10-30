package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateAccountPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateProgramChangeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class GetCandidateAccountUseCaseImpl implements GetCandidateAccountUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidateAccountPort candidateAccountPort;

	private final CandidateProgramChangeRepository programChangeRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	public GetCandidateAccountUseCaseImpl(CandidateRepository candidateRepository,
			CandidateAccountPort candidateAccountPort, CandidateProgramChangeRepository programChangeRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort) {
		this.candidateRepository = candidateRepository;
		this.candidateAccountPort = candidateAccountPort;
		this.programChangeRepository = programChangeRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public CandidateAccount get(UUID candidateId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		List<CandidateAccountPort.Charge> charges = candidateAccountPort.findCharges(candidateId);
		// Los cobros de la ficha son el de la ficha vigente, la diferencia pendiente
		// y los que tocó un cambio de carrera; lo demás es otro concepto.
		List<CandidateProgramChange> changes = programChangeRepository.findByCandidateId(candidateId).stream()
				.sorted(Comparator.comparing(CandidateProgramChange::getChangedAt).reversed()).toList();
		Set<UUID> fichaCharges = new HashSet<>();
		fichaCharges.add(candidate.getFichaPaymentId());
		changes.forEach(change -> {
			fichaCharges.add(change.getFichaPaymentId());
			fichaCharges.add(change.getNewPaymentId());
		});

		Map<UUID, String> references = new HashMap<>();
		charges.forEach(charge -> references.put(charge.paymentId(), charge.referenceNumber()));

		List<ChargeView> chargeViews = charges.stream()
				.sorted(Comparator.comparing(CandidateAccountPort.Charge::createdAt,
						Comparator.nullsLast(Comparator.naturalOrder())).reversed())
				.map(charge -> toView(charge, role(charge, candidate, fichaCharges)))
				.sorted(Comparator.comparingInt(view -> view.role().ordinal()))
				.toList();

		List<CandidateAccountPort.Credit> credits = candidateAccountPort.findCredits(candidateId);
		BigDecimal available = credits.stream().filter(credit -> "AVAILABLE".equals(credit.status()))
				.map(CandidateAccountPort.Credit::amount).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		List<CreditView> creditViews = credits.stream()
				.sorted(Comparator.comparing(CandidateAccountPort.Credit::createdAt,
						Comparator.nullsLast(Comparator.naturalOrder())).reversed())
				.map(credit -> new CreditView(credit.amount(), credit.status(), references.get(credit.originPaymentId()),
						credit.appliedToPaymentId() == null ? null : references.get(credit.appliedToPaymentId()),
						credit.createdAt()))
				.toList();

		Map<UUID, String> programNames = new HashMap<>();
		List<ProgramChangeView> changeViews = changes.stream()
				.map(change -> new ProgramChangeView(programName(change.getFromConfigId(), programNames),
						programName(change.getToConfigId(), programNames), change.isWasPaid(),
						change.getPreviousAmount(), change.getPaidAmount(), change.getNewAmount(), change.getToPay(),
						change.getCreditLeft(), change.getChangedAt()))
				.toList();

		return new CandidateAccount(candidateId, available, chargeViews, creditViews, changeViews);
	}

	private static ChargeRole role(CandidateAccountPort.Charge charge, Candidate candidate, Set<UUID> fichaCharges) {
		if (charge.paymentId().equals(candidate.getProgramChangePaymentId())) {
			return ChargeRole.PENDING_DIFFERENCE;
		}
		if (charge.paymentId().equals(candidate.getFichaPaymentId())) {
			return ChargeRole.CURRENT_FICHA;
		}
		return fichaCharges.contains(charge.paymentId()) ? ChargeRole.HISTORY : ChargeRole.OTHER;
	}

	private static ChargeView toView(CandidateAccountPort.Charge charge, ChargeRole role) {
		return new ChargeView(charge.paymentId(), role, charge.conceptCode(), charge.referenceNumber(),
				charge.programName(), charge.status(), charge.originalAmount(), charge.discountAmount(),
				charge.discountReason(), charge.amountDue(), charge.amountPaid(), charge.balance(), charge.dueAt(),
				charge.createdAt(),
				charge.entries().stream().map(entry -> new EntryView(entry.amount(), entry.method(), entry.channel(),
						entry.receiptNumber(), entry.reference(), entry.paidAt())).toList());
	}

	private String programName(UUID configId, Map<UUID, String> cache) {
		if (configId == null) {
			return null;
		}
		return cache.computeIfAbsent(configId, id -> programAdmissionConfigQueryPort.findById(id)
				.map(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo::programName).orElse(null));
	}
}
