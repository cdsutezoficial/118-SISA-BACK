package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	Optional<AcademicProgram> findByCode(String code);

	/**
	 * Backs the {@code (offerName, modality)} composite uniqueness check
	 * (spec: "The pair (offerName, modality) MUST be unique across all
	 * programs").
	 */
	Optional<AcademicProgram> findByOfferNameAndModality(String offerName, ProgramModality modality);

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
}
