package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link AcademicPlan}. The sole out-port for the
 * whole aggregate — {@code PlanLevel}/{@code Subject} children have no
 * repository of their own; every child mutation loads the plan through this
 * port, invokes a mutator on it, then saves it again (cascade persists the
 * child). Implemented by a JPA adapter in Phase 5.
 */
public interface AcademicPlanRepository {

	AcademicPlan save(AcademicPlan plan);

	Optional<AcademicPlan> findById(UUID id);

	/**
	 * Backs the {@code version} uniqueness-within-{@code programId} check
	 * (spec: "version MUST be unique within the same programId").
	 */
	Optional<AcademicPlan> findByProgramIdAndVersion(UUID programId, String version);

	/**
	 * Filterable, paginated query backing {@code ListAcademicPlansUseCase}.
	 */
	PlanSearchPage search(PlanSearchCriteria criteria);

	/**
	 * @param programId optional — filters to plans belonging to this program
	 * @param status    optional — filters to plans with this exact status
	 * @param search    optional free-text match
	 * @param page      zero-based page index
	 * @param size      page size
	 */
	record PlanSearchCriteria(UUID programId, PlanStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link AcademicPlan} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record PlanSearchPage(List<AcademicPlan> content, long totalElements, int totalPages) {
	}
}
