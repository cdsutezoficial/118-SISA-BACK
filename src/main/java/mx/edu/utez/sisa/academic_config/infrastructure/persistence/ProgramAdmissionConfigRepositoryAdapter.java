package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link ProgramAdmissionConfigRepository} adapter delegating to
 * {@link ProgramAdmissionConfigJpaRepository}. Results are sorted by
 * {@code opensAt} ascending then {@code id} ascending — this aggregate has no
 * {@code code}/{@code name} field, and {@code opensAt} (the ticket-sales
 * window opening instant) is the most useful chronological ordering for a
 * listing of admission processes, same tie-breaker rationale as
 * {@code PaymentConceptRepositoryAdapter}.
 */
@Component
public class ProgramAdmissionConfigRepositoryAdapter implements ProgramAdmissionConfigRepository {

	private final ProgramAdmissionConfigJpaRepository jpaRepository;

	public ProgramAdmissionConfigRepositoryAdapter(ProgramAdmissionConfigJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public ProgramAdmissionConfig save(ProgramAdmissionConfig config) {
		return jpaRepository.save(config);
	}

	@Override
	public Optional<ProgramAdmissionConfig> findByProgramIdAndPeriodId(UUID programId, UUID periodId) {
		return jpaRepository.findByProgramIdAndPeriodId(programId, periodId);
	}

	@Override
	public Optional<ProgramAdmissionConfig> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public ProgramAdmissionConfigSearchPage search(ProgramAdmissionConfigSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "opensAt").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<ProgramAdmissionConfig> page = jpaRepository.search(criteria.status(), criteria.programId(), pageRequest);
		return new ProgramAdmissionConfigSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
