package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AcademicPeriodRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows) — same convention as
 * {@code SubjectClassificationJpaRepository}.
 */
public interface AcademicPeriodJpaRepository extends JpaRepository<AcademicPeriod, UUID> {

	/**
	 * Backs the {@code (year, periodNumber)} uniqueness rule — same
	 * convention as {@code SubjectClassificationJpaRepository#findByCodeIgnoreCase}.
	 */
	Optional<AcademicPeriod> findByYearAndPeriodNumber(int year, int periodNumber);

	/**
	 * Backs {@code ListAcademicPeriodsUseCase}, mirroring
	 * {@code SubjectClassificationJpaRepository#search}: both filters are
	 * optional via the {@code (:param IS NULL OR ...)} pattern, with an
	 * explicit {@code countQuery} for consistency with the rest of the codebase.
	 */
	@Query(value = """
			SELECT p FROM AcademicPeriod p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(p) FROM AcademicPeriod p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<AcademicPeriod> search(@Param("status") PeriodStatus status, @Param("search") String search,
			Pageable pageable);

	/**
	 * Reference-catalog read backing {@code GET /periods/options} (transversal
	 * design: "Roles y Permisos — patrón reference"). Returns only
	 * {@link PeriodStatus#ACTIVE} periods as a minimal
	 * {@link PeriodOptionProjection} — {@code id}, {@code name} (the label)
	 * and {@code year} (doubling as the picker's secondary identifier),
	 * ordered by year (descending) then name. Interface projection avoids
	 * loading the full {@code AcademicPeriod} (no startDate/endDate,
	 * enrollment windows, periodNumber/type).
	 */
	List<PeriodOptionProjection> findByStatusOrderByYearDescNameAsc(PeriodStatus status);

	/**
	 * Minimal projection for reference pickers — maps to {@code OptionResponse}.
	 */
	interface PeriodOptionProjection {
		UUID getId();

		String getName();

		int getYear();
	}
}
