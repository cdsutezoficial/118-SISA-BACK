package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AcademicPlanRepository} adapter delegating to
 * {@link AcademicPlanJpaRepository}. Results are sorted by {@code version}
 * ascending (design.md — File Changes: "JPA repo + adapter, sort by
 * version"), mirroring {@code AcademicProgramRepositoryAdapter}. Saving the
 * {@code AcademicPlan} aggregate cascades INSERT/UPDATE/DELETE to its owned
 * {@code PlanLevel}/{@code Subject} children (design.md — Decision: "Child
 * persistence — JPA composition").
 *
 * <p>
 * {@link #save} uses {@code saveAndFlush}, not plain {@code save} — apply-phase
 * discovery: when a new {@code PlanLevel}/{@code Subject} is added to an
 * already-managed {@code AcademicPlan} (fetched earlier in the same
 * transaction via {@code findById}), a plain {@code save()} call is a no-op
 * on an already-managed entity and does NOT force Hibernate to cascade the
 * INSERT immediately — the generated {@code UUID} id for the new child is
 * only actually assigned at flush time. {@code AddPlanLevelUseCaseImpl}/
 * {@code AddSubjectToPlanUseCaseImpl} return the created child directly
 * (design.md — Decision: "Boundary enforcement" rationale) and need that id
 * populated in the same call for their response DTO — an explicit flush
 * here is required for that to work correctly.
 */
@Component
public class AcademicPlanRepositoryAdapter implements AcademicPlanRepository {

	private final AcademicPlanJpaRepository jpaRepository;

	public AcademicPlanRepositoryAdapter(AcademicPlanJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public AcademicPlan save(AcademicPlan plan) {
		return jpaRepository.saveAndFlush(plan);
	}

	@Override
	public Optional<AcademicPlan> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<AcademicPlan> findByProgramIdAndVersion(UUID programId, String version) {
		return jpaRepository.findByProgramIdAndVersion(programId, version);
	}

	@Override
	public PlanSearchPage search(PlanSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "version"));
		Page<AcademicPlan> page = jpaRepository.search(criteria.programId(), criteria.status(), criteria.search(),
				pageRequest);
		return new PlanSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
