package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateProgramChangeRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JPA-backed {@link CandidateProgramChangeRepository} adapter delegating to
 * {@link CandidateProgramChangeJpaRepository}.
 */
@Component
public class CandidateProgramChangeRepositoryAdapter implements CandidateProgramChangeRepository {

	private final CandidateProgramChangeJpaRepository jpaRepository;

	public CandidateProgramChangeRepositoryAdapter(CandidateProgramChangeJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public CandidateProgramChange save(CandidateProgramChange change) {
		return jpaRepository.save(change);
	}

	@Override
	public long countByCandidateId(UUID candidateId) {
		return jpaRepository.countByCandidateId(candidateId);
	}
}
