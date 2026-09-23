package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only assembler behind {@code GET /candidates/{id}}, the ficha PDF and
 * the payment emails (see {@link GetCandidateFichaUseCase} for the consumer
 * map). No write access — a pure query interactor, so it is safe to call from
 * the public portal. Missing {@code Candidate} or its {@code ADMISSION_FICHA}
 * payment yields an empty optional (the web layer maps to 404); the program
 * name resolves through {@code academic_config}'s minimal lookup port and may
 * be absent if the config disappears (name falls back to the ficha itself).
 */
public class GetCandidateFichaUseCaseImpl implements GetCandidateFichaUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	public GetCandidateFichaUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public FichaData get(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.flatMap(this::assemble)
				.orElse(null);
	}

	private java.util.Optional<FichaData> assemble(Candidate candidate) {
		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidate.getId()).orElse(null);
		if (payment == null) {
			return java.util.Optional.empty();
		}
		Person person = candidatePersonRepository.findById(candidate.getPersonId()).orElse(null);
		if (person == null) {
			return java.util.Optional.empty();
		}
		String programName = programAdmissionConfigQueryPort.findById(candidate.getAdmissionConfigId())
				.map(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo::programName)
				.orElse(null);
		return java.util.Optional.of(new FichaData(candidate.getId(), candidate.getFolio(), candidate.getStatus(),
				candidate.getRegisteredAt(), candidate.getAdmissionConfigId(), programName, person.getCurp(),
				person.getFirstName(), person.getLastName1(), person.getLastName2(), person.getPersonalEmail(),
				payment.getReferenceNumber(), payment.getAmount(), payment.getPaymentDeadline(),
				payment.getPaymentStatus(), payment.getReceiptNumber(), payment.getPaidAt()));
	}
}