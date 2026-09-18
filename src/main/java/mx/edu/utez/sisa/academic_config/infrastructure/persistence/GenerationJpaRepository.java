package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link GenerationRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows) — same convention as
 * {@code AcademicPeriodJpaRepository}.
 */
public interface GenerationJpaRepository extends JpaRepository<Generation, UUID> {

	/**
	 * Backs the {@code (programId, number)} uniqueness rule — same convention
	 * as {@code AcademicPeriodJpaRepository#findByYearAndPeriodNumber}.
	 */
	Optional<Generation> findByProgramIdAndNumber(UUID programId, int number);

	/**
	 * Backs {@code ListGenerationsUseCase}, mirroring
	 * {@code AcademicPlanJpaRepository#search}: all three filters are optional
	 * via the {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery} for consistency with the rest of the codebase.
	 * {@code search} matches against {@code code} — this aggregate has no
	 * {@code name} field of its own.
	 */
	@Query(value = """
			SELECT g FROM Generation g
			WHERE (:status IS NULL OR g.status = :status)
			  AND (:search IS NULL OR LOWER(g.code) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:programId IS NULL OR g.programId = :programId)
			""",
			countQuery = """
			SELECT COUNT(g) FROM Generation g
			WHERE (:status IS NULL OR g.status = :status)
			  AND (:search IS NULL OR LOWER(g.code) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:programId IS NULL OR g.programId = :programId)
			""")
	Page<Generation> search(@Param("status") GenerationStatus status, @Param("search") String search,
			@Param("programId") UUID programId, Pageable pageable);

	/**
	 * Reference-catalog read backing {@code GET /generations/options}
	 * (transversal design: "Roles y Permisos — patrón reference"). Returns only
	 * {@link GenerationStatus#ACTIVE} generations, optional {@code programId}
	 * filter, as a minimal {@link GenerationOptionProjection}. {@code code}
	 * doubles as the {@code OptionResponse#label} — this aggregate has no
	 * {@code name} field of its own (see {@code OptionResponse} javadoc on the
	 * optional code, omitted here since {@code code} is already the label).
	 */
	List<GenerationOptionProjection> findByStatusOrderByCodeAsc(GenerationStatus status);

	/**
	 * Same as {@link #findByStatusOrderByCodeAsc(GenerationStatus)} but scoped
	 * to one program — consumed by pickers that already know the career (e.g.
	 * {@code ?programId=} on {@code GET /generations/options}).
	 */
	List<GenerationOptionProjection> findByProgramIdAndStatusOrderByCodeAsc(UUID programId, GenerationStatus status);

	/**
	 * Minimal projection for reference pickers — maps to {@code OptionResponse}.
	 */
	interface GenerationOptionProjection {
		UUID getId();

		String getCode();
	}
}
