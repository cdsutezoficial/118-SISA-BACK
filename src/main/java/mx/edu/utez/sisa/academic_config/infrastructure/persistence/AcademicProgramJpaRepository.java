package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AcademicProgramRepositoryAdapter}.
 */
public interface AcademicProgramJpaRepository extends JpaRepository<AcademicProgram, UUID> {

	/**
	 * Backs the {@code code} uniqueness check (spec: "code MUST be unique
	 * across all programs"). No case-insensitivity requirement for this
	 * aggregate — unlike {@code AcademicDivisionJpaRepository#findByCodeIgnoreCase}.
	 */
	Optional<AcademicProgram> findByCodeIgnoreCase(String code);

	/**
	 * Backs the {@code (offerName, modality)} composite uniqueness check
	 * (spec: "The pair (offerName, modality) MUST be unique across all
	 * programs").
	 */
	Optional<AcademicProgram> findByOfferNameIgnoreCaseAndModality(String offerName, ProgramModality modality);

	/**
	 * Backs {@code ListAcademicProgramsUseCase}. {@code divisionId} is a plain
	 * optional-equality filter (design.md — Interfaces/Contracts note: "no
	 * EXISTS subquery needed"), same pattern as {@code status} and
	 * {@code search}.
	 */
	@Query(value = """
			SELECT p FROM AcademicProgram p
			WHERE (:divisionId IS NULL OR p.divisionId = :divisionId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.offerName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(p) FROM AcademicProgram p
			WHERE (:divisionId IS NULL OR p.divisionId = :divisionId)
			  AND (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.offerName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<AcademicProgram> search(@Param("divisionId") UUID divisionId, @Param("status") ProgramStatus status,
			@Param("search") String search, Pageable pageable);

	/**
	 * Grouped count backing {@code AcademicProgramRepository#countProgramsByDivisionIds}
	 * — one aggregate query for the whole page of {@code GET /divisions},
	 * instead of a {@code COUNT} per division.
	 */
	@Query("""
			SELECT p.divisionId, COUNT(p)
			FROM AcademicProgram p
			WHERE p.divisionId IN :divisionIds
			GROUP BY p.divisionId
			""")
	List<Object[]> countProgramsByDivisionIdIn(@Param("divisionIds") Collection<UUID> divisionIds);

	/**
	 * Reference-catalog read backing {@code GET /programs/options} (transversal
	 * design: "Roles y Permisos — patrón reference"). Returns only
	 * {@code ACTIVE} programs as a minimal {@link ProgramOptionProjection}, so
	 * reference pickers never load management fields (description, dgpCode,
	 * pagination). Using an interface projection keeps the query light — the
	 * full {@code AcademicProgram} entity is intentionally not fetched. See
	 * commit "add GET /programs/options" for the security matcher note: the
	 * {@code /options} path is matched BEFORE the blanket {@code GET /programs/**}
	 * rule in {@code SecurityFilterConfig}.
	 */
	List<ProgramOptionProjection> findByStatusOrderByNameAsc(ProgramStatus status);

	/**
	 * Same reference-catalog read as {@link #findByStatusOrderByNameAsc},
	 * additionally filtered to one division (picker "Programa" con
	 * {@code ?divisionId=}).
	 */
	List<ProgramOptionProjection> findByStatusAndDivisionIdOrderByNameAsc(ProgramStatus status, UUID divisionId);

	/**
	 * Minimal projection for reference pickers — {@code id}, {@code name}
	 * (the label), {@code code} and {@code modality} (the picker renders
	 * "DSM — Desarrollo de Software (Presencial)"). Maps to
	 * {@code ProgramOptionResponse}. Interface projection is deliberate
	 * (design.md — glide-light reads).
	 */
	interface ProgramOptionProjection {
		UUID getId();

		String getName();

		String getCode();

		ProgramModality getModality();
	}
}
