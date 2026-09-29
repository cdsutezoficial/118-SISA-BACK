package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AcademicPlanRepositoryAdapter}.
 */
public interface AcademicPlanJpaRepository extends JpaRepository<AcademicPlan, UUID> {

	/**
	 * Backs the {@code version} uniqueness-within-{@code programId} check
	 * (spec: "version MUST be unique within the same programId").
	 */
	Optional<AcademicPlan> findByProgramIdAndVersion(UUID programId, String version);

	/**
	 * Backs {@code ListAcademicPlansUseCase}. {@code programId} is a plain
	 * optional-equality filter, same pattern as {@code status} and
	 * {@code search} (design.md — mirrors {@code AcademicProgramJpaRepository#search}).
	 * {@code search} matches against {@code version} and {@code titulationKey}
	 * — this aggregate has no {@code name}/{@code code} field of its own.
	 */
	@Query(value = """
			SELECT p FROM AcademicPlan p
			WHERE (:programId IS NULL OR p.programId = :programId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.version) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.titulationKey) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(p) FROM AcademicPlan p
			WHERE (:programId IS NULL OR p.programId = :programId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.version) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.titulationKey) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<AcademicPlan> search(@Param("programId") UUID programId, @Param("status") PlanStatus status,
			@Param("search") String search, Pageable pageable);

	/**
	 * Reference-catalog read backing {@code GET /plans/options} (transversal
	 * design: "Roles y Permisos — patrón reference"): only
	 * {@link PlanStatus#ACTIVE} plans, optional {@code programId} filter, as a
	 * minimal {@link PlanOptionProjection}. {@code version} doubles as the
	 * {@code OptionResponse#label} — plans have no name/code field of their own
	 * (see {@code OptionResponse} javadoc on the optional code).
	 */
	List<PlanOptionProjection> findByStatusOrderByVersionAsc(PlanStatus status);

	/**
	 * Same as {@link #findByStatusOrderByVersionAsc(PlanStatus)} but scoped to
	 * one program — consumed by pickers that already know the career (e.g.
	 * {@code ?programId=} on {@code GET /plans/options}).
	 */
	List<PlanOptionProjection> findByProgramIdAndStatusOrderByVersionAsc(UUID programId, PlanStatus status);

	/**
	 * Minimal projection for reference pickers — maps to {@code OptionResponse}.
	 */
	interface PlanOptionProjection {
		UUID getId();

		String getVersion();
	}
}
