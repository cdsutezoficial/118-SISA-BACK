package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
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
	long countByPeriodIdAndStatus(UUID periodId, GroupStatus status);

	/**
	 * Backs the {@code (generationId, code)} uniqueness check of
	 * {@code CreateGroupUseCaseImpl}/{@code UpdateGroupUseCaseImpl}.
	 *
	 * <p>
	 * Derived query, not JPQL: a derived {@code findBy...} lets Hibernate bind
	 * the value as a parameter, whereas the same predicate written by hand would
	 * have to go through a string concat. No {@code @Query} needed and no chance
	 * of the concat introducing a syntax error.
	 */
	Optional<Group> findByGenerationIdAndCode(UUID generationId, String code);

	/**
	 * Backs the bulk-creation letter allocator: given every {@code code} already
	 * in use by this generation, pick the next free ones. Returns the codes, not
	 * the entities — the allocator only needs the identifier, and fetching whole
	 * rows to read one column would be wasteful.
	 *
	 * <p>
	 * <b>No puede ser query derivada</b> (D1 de las incidencias 2026-10-06): un
	 * {@code findCodesByGenerationId} sin {@code @Query} le pide a Spring Data
	 * una propiedad {@code codes} que {@code Group} no tiene, y esa
	 * {@code PropertyReferenceException} se lanza dentro de la ruta masiva
	 * ({@code previewGroupCodes}/{@code createGroupsBulk}) y cae en el handler
	 * de última instancia de {@code identity}, que responde 500. Con JPQL
	 * explícito el predicado es la columna {@code code} y nada más.
	 */
	@Query("SELECT g.code FROM Group g WHERE g.generationId = :generationId")
	List<String> findCodesByGenerationId(@Param("generationId") UUID generationId);
}
