package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link CandidateRepository} adapter delegating to
 * {@link CandidateJpaRepository}.
 */
@Component
public class CandidateRepositoryAdapter implements CandidateRepository {

	private final CandidateJpaRepository jpaRepository;

	public CandidateRepositoryAdapter(CandidateJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Candidate save(Candidate candidate) {
		return jpaRepository.save(candidate);
	}

	@Override
	public Optional<Candidate> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public long countByFolioStartingWith(String prefix) {
		return jpaRepository.countByFolioStartingWith(prefix);
	}

	@Override
	public Optional<Candidate> findByFolio(String folio) {
		return jpaRepository.findByFolio(folio);
	}
}