package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateAccountPort;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.StatementPayment;
import mx.edu.utez.sisa.finance.domain.port.in.ListCreditsUseCase;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lee de Finanzas los cargos (con sus abonos y descuento) y los saldos a favor
 * del candidato, y les pone el nombre de la carrera a la que se cobró.
 */
@Component
public class CandidateAccountAdapter implements CandidateAccountPort {

	private final GetAccountStatementUseCase getAccountStatementUseCase;

	private final ListCreditsUseCase listCreditsUseCase;

	private final AcademicProgramNameLookupJpaRepository programJpaRepository;

	public CandidateAccountAdapter(GetAccountStatementUseCase getAccountStatementUseCase,
			ListCreditsUseCase listCreditsUseCase, AcademicProgramNameLookupJpaRepository programJpaRepository) {
		this.getAccountStatementUseCase = getAccountStatementUseCase;
		this.listCreditsUseCase = listCreditsUseCase;
		this.programJpaRepository = programJpaRepository;
	}

	@Override
	public List<Charge> findCharges(UUID candidateId) {
		Map<UUID, String> programNames = new HashMap<>();
		return getAccountStatementUseCase.getAccountStatement(candidateId, null).payments().stream()
				.map(payment -> toCharge(payment, programNames)).toList();
	}

	@Override
	public List<Credit> findCredits(UUID candidateId) {
		return listCreditsUseCase.listByCandidate(candidateId).stream()
				.map(credit -> new Credit(credit.amount(), credit.status().name(), credit.originPaymentId(),
						credit.appliedToPaymentId(), credit.createdAt()))
				.toList();
	}

	private Charge toCharge(StatementPayment payment, Map<UUID, String> programNames) {
		String programName = payment.programId() == null ? null
				: programNames.computeIfAbsent(payment.programId(),
						id -> programJpaRepository.findById(id).map(AcademicProgram::getName).orElse(null));
		return new Charge(payment.id(), payment.conceptCode(), payment.referenceNumber(), programName,
				payment.status().name(), payment.originalAmount(),
				payment.benefit() == null ? null : payment.benefit().amount(),
				payment.benefit() == null ? null : payment.benefit().reason(), payment.amountDue(),
				payment.amountPaid(), payment.balance(), payment.dueAt(), payment.createdAt(),
				payment.entries().stream()
						.map(entry -> new Entry(entry.amount(), entry.method() == null ? null : entry.method().name(),
								entry.channel() == null ? null : entry.channel().name(), entry.receiptNumber(),
								entry.reference(), entry.paidAt()))
						.toList());
	}
}
