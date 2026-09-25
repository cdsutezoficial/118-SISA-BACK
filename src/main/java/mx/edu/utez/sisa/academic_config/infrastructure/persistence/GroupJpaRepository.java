package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data interface backing {@link GroupRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows) — same convention as
 * {@code GenerationJpaRepository}.
 */
public interface GroupJpaRepository extends JpaRepository<Group, UUID> {

	/**
	 * Backs {@code ListGroupsUseCase}, mirroring
	 * {@code GenerationJpaRepository#search}: all four filters are optional via
	 * the {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery} for consistency with the rest of the codebase.
	 * {@code search} matches against {@code code} — this aggregate has no
	 * {@code name} field of its own.
	 */
	@Query(value = """
			SELECT g FROM Group g
			WHERE (:status IS NULL OR g.status = :status)
			  AND (:search IS NULL OR LOWER(g.code) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:programId IS NULL OR g.programId = :programId)
			  AND (:generationId IS NULL OR g.generationId = :generationId)
			""",
			countQuery = """
			SELECT COUNT(g) FROM Group g
			WHERE (:status IS NULL OR g.status = :status)
			  AND (:search IS NULL OR LOWER(g.code) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:programId IS NULL OR g.programId = :programId)
			  AND (:generationId IS NULL OR g.generationId = :generationId)
			""")
	Page<Group> search(@Param("status") GroupStatus status, @Param("search") String search,
			@Param("programId") UUID programId, @Param("generationId") UUID generationId, Pageable pageable);

	/**
	 * Backs {@code GetConfigurationStatisticsUseCase}: counts the groups
	 * assigned to one {@code periodId} — the dashboard's "grupos activos"
	 * counter for the current period.
	 */
	long countByPeriodId(UUID periodId);
}
