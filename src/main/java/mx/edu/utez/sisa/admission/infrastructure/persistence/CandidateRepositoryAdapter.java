package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
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

	@Override
	public List<Candidate> findAllByStatus(CandidateStatus status) {
		return jpaRepository.findAllByStatus(status);
	}

	@Override
	public List<Candidate> findAllByPersonId(UUID personId) {
		return jpaRepository.findAllByPersonId(personId);
	}

	@Override
	public CandidateSearchPage search(CandidateSearchCriteria criteria) {
		// Newest first, id as the tie-breaker so two fichas registered in the same
		// instant keep a total order and cannot jump between pages on re-query.
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.DESC, "registeredAt").and(Sort.by(Sort.Direction.DESC, "id")));
		String search = criteria.search() == null || criteria.search().isBlank() ? null : criteria.search().trim();
		Page<Candidate> page = jpaRepository.search(criteria.status(), criteria.programId(), criteria.periodId(),
				criteria.divisionId(), search, pageRequest);
		return new CandidateSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}