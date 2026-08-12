package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data interface backing {@link HighSchoolTypeRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows) — same shape as
 * {@code OutreachChannelJpaRepository}.
 */
public interface HighSchoolTypeJpaRepository extends JpaRepository<HighSchoolType, UUID> {

	/**
	 * Backs {@code ListHighSchoolTypesUseCase}, mirroring
	 * {@code OutreachChannelJpaRepository#search}: both filters are optional
	 * via the {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery} for consistency with the rest of the codebase.
	 */
	@Query(value = """
			SELECT h FROM HighSchoolType h
			WHERE (:status IS NULL OR h.status = :status)
			  AND (:search IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(h) FROM HighSchoolType h
			WHERE (:status IS NULL OR h.status = :status)
			  AND (:search IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<HighSchoolType> search(@Param("status") HighSchoolTypeStatus status, @Param("search") String search,
			Pageable pageable);
}
