package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AcademicDivisionRepositoryAdapter}.
 */
public interface AcademicDivisionJpaRepository extends JpaRepository<AcademicDivision, UUID> {

	/**
	 * Case-insensitive lookup — backs {@code AcademicDivisionRepository#findByCode}'s
	 * documented case-insensitive contract.
	 */
	Optional<AcademicDivision> findByCodeIgnoreCase(String code);

	/**
	 * Case-insensitive lookup — see {@link #findByCodeIgnoreCase(String)}.
	 */
	Optional<AcademicDivision> findByNameIgnoreCase(String name);

	/**
	 * Backs {@code ListAcademicDivisionsUseCase}. Unlike {@code identity.UserJpaRepository#search},
	 * no correlated {@code EXISTS} subquery is needed — this slice has no
	 * child collection to filter across (design.md's Interfaces/Contracts
	 * note), so a plain filtered/paginated query suffices. Both filters are
	 * optional via the {@code (:param IS NULL OR ...)} pattern.
	 *
	 * <p>An explicit {@code countQuery} is supplied for consistency with the
	 * rest of the codebase, even though Spring Data's derived count query
	 * would likely work fine here too.
	 */
	@Query(value = """
			SELECT d FROM AcademicDivision d
			WHERE (:status IS NULL OR d.status = :status)
			  AND (:search IS NULL
			       OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(d) FROM AcademicDivision d
			WHERE (:status IS NULL OR d.status = :status)
			  AND (:search IS NULL
			       OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<AcademicDivision> search(@Param("status") DivisionStatus status, @Param("search") String search,
			Pageable pageable);
}
