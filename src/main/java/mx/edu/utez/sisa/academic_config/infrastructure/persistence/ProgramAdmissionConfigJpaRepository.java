package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link ProgramAdmissionConfigRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows) — same convention
 * as {@code GenerationJpaRepository}.
 */
public interface ProgramAdmissionConfigJpaRepository extends JpaRepository<ProgramAdmissionConfig, UUID> {

	/**
	 * Backs the {@code (programId, periodId)} uniqueness rule — same
	 * convention as {@code GenerationJpaRepository#findByProgramIdAndNumber}.
	 */
	Optional<ProgramAdmissionConfig> findByProgramIdAndPeriodId(UUID programId, UUID periodId);

	/**
	 * Backs {@code ListProgramAdmissionConfigsUseCase}, mirroring
	 * {@code GenerationJpaRepository#search}: both filters are optional via the
	 * {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery} for consistency with the rest of the codebase. Unlike
	 * {@code Generation}, there is no free-text {@code search} filter — this
	 * aggregate has no {@code code}/{@code name} field.
	 */
	@Query(value = """
			SELECT p FROM ProgramAdmissionConfig p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:programId IS NULL OR p.programId = :programId)
			""",
			countQuery = """
			SELECT COUNT(p) FROM ProgramAdmissionConfig p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:programId IS NULL OR p.programId = :programId)
			""")
	Page<ProgramAdmissionConfig> search(@Param("status") ProgramAdmissionConfigStatus status,
			@Param("programId") UUID programId, Pageable pageable);
}
