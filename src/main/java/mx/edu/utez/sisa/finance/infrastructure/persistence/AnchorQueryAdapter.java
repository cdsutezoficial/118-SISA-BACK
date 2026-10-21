package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort;
import mx.edu.utez.sisa.shared.model.Person;
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

	private final FinancePersonJpaRepository personJpaRepository;

	public AnchorQueryAdapter(FinanceCandidateJpaRepository candidateJpaRepository,
			FinancePersonJpaRepository personJpaRepository) {
		this.candidateJpaRepository = candidateJpaRepository;
		this.personJpaRepository = personJpaRepository;
	}

	@Override
	public Optional<PersonQueryPort.PersonView> findPersonByCandidateId(UUID candidateId) {
		return candidateJpaRepository.findById(candidateId)
				.flatMap(candidate -> personJpaRepository.findById(candidate.getPersonId()))
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

	private static PersonQueryPort.PersonView toView(Person person) {
		return new PersonQueryPort.PersonView(person.getId(), person.getCurp(), person.getFirstName(),
				person.getLastName1(), person.getLastName2());
	}
}
