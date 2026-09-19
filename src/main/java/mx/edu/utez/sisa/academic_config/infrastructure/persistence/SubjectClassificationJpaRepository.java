package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link SubjectClassificationRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows).
 */
public interface SubjectClassificationJpaRepository extends JpaRepository<SubjectClassification, UUID> {

	/**
	 * Case-insensitive lookup — backs {@code SubjectClassificationRepository#findByCode}'s
	 * documented case-insensitive contract, same convention as
	 * {@code AcademicDivisionJpaRepository#findByCodeIgnoreCase}.
	 */
	Optional<SubjectClassification> findByCodeIgnoreCase(String code);

	/**
	 * Backs {@code ListSubjectClassificationsUseCase}, mirroring
	 * {@code AcademicDivisionJpaRepository#search}: both filters are optional
	 * via the {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery} for consistency with the rest of the codebase.
	 */
	@Query(value = """
			SELECT c FROM SubjectClassification c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL
			       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(c) FROM SubjectClassification c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL
			       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<SubjectClassification> search(@Param("status") ClassificationStatus status, @Param("search") String search,
			Pageable pageable);

	/**
	 * Reference-catalog read backing {@code GET /subject-classifications/options}
	 * (transversal design: "Roles y Permisos — patrón reference"). Returns
	 * only {@link ClassificationStatus#ACTIVE} classifications as a minimal
	 * {@link ClassificationOptionProjection} — {@code id}, {@code name} (the
	 * label), {@code code} — ordered by name. Interface projection avoids
	 * loading the full {@code SubjectClassification} (this aggregate has no
	 * other fields, but keeps the reference read uniform with the other
	 * catalogs).
	 */
	List<ClassificationOptionProjection> findByStatusOrderByNameAsc(ClassificationStatus status);

	/**
	 * Minimal projection for reference pickers — maps to {@code OptionResponse}.
	 */
	interface ClassificationOptionProjection {
		UUID getId();

		String getName();

		String getCode();
	}
}
