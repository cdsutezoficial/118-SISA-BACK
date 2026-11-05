package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AnchorQueryPort} adapter. Navigates a {@code candidateId}
 * anchor through admission's {@code candidate} table to the shared-kernel
 * {@code Person}.
 *
 * <p><b>Students:</b> the {@code enrollment} context is not implemented yet, so
 * there is no {@code student} table to read. The student methods answer
 * (empty / {@code false}) until that aggregate lands; no payment can carry a
 * {@code studentId} before then, so this is a faithful "no students exist"
 * answer rather than a stub.
 */
@Component
public class AnchorQueryAdapter implements AnchorQueryPort {

	private final FinanceCandidateJpaRepository candidateJpaRepository;

	public AnchorQueryAdapter(FinanceCandidateJpaRepository candidateJpaRepository) {
		this.candidateJpaRepository = candidateJpaRepository;
	}

	@Override
	public Optional<PersonQueryPort.PersonView> findPayerByCandidateId(UUID candidateId) {
		return candidateJpaRepository.findById(candidateId)
				.map(AnchorQueryAdapter::toView);
	}

	@Override
	public Optional<PersonQueryPort.PersonView> findPersonByStudentId(UUID studentId) {
		return Optional.empty();
	}

	@Override
	public boolean candidateExists(UUID candidateId) {
		return candidateId != null && candidateJpaRepository.existsById(candidateId);
	}

	@Override
	public boolean studentExists(UUID studentId) {
		return false;
	}

	private static PersonQueryPort.PersonView toView(Candidate candidate) {
		return new PersonQueryPort.PersonView(candidate.getId(), candidate.getCurp(), candidate.getFirstName(),
				candidate.getLastName1(), candidate.getLastName2());
	}
}
