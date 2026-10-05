package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link CandidateRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows). {@code countByFolioStartingWith}
 * backs the use case's calendar-year-scoped folio sequence — see
 * {@code RegisterCandidateUseCaseImpl#generateFolio}.
 */
public interface CandidateJpaRepository extends JpaRepository<Candidate, UUID> {

	/**
	 * Counts existing folios under the given prefix (e.g. {@code "ADM-2026-"})
	 * — the {@code seq} component of folio {@code ADM-{year}-{seq}:06d}.
	 */
	long countByFolioStartingWith(String prefix);

	/**
	 * Exact folio lookup backing {@code AccessFichaPaymentUseCase}: the
	 * "vuelve a pagar mi ficha" entry point identifies the candidate by folio
	 * before checking the CURP suffix. Derived query — {@code folio} is a
	 * unique business key.
	 */
	Optional<Candidate> findByFolio(String folio);

	/**
	 * Derived query backing the ficha-expiry sweep:
	 * {@code ExpireStaleFichaPaymentsUseCase} reads the {@code REGISTERED} fichas
	 * and expires the ones whose private payment window has passed.
	 */
	List<Candidate> findAllByStatus(CandidateStatus status);

	/**
	 * Derived query backing the CURP re-registration lock: registration reads a
	 * person's fichas and blocks only when one of them is still alive (a
	 * {@code PAID} ficha, or a {@code REGISTERED} one inside its payment window).
	 */
	List<Candidate> findAllByPersonId(UUID personId);

	/**
	 * Paginated candidate search backing {@code ListCandidatesUseCase}. Every
	 * filter is optional ({@code null} = no constraint) and program/period/
	 * division are expressed as correlated {@code EXISTS} subqueries rather than
	 * joins, so the row set is never multiplied when a candidate's config
	 * happens to satisfy more than one condition.
	 *
	 * <p>{@code ProgramAdmissionConfig} and {@code AcademicProgram} are
	 * {@code academic_config} entities with NO JPA relation to {@code Candidate}
	 * (only the bare {@code admission_config_id} column exists), which is why the
	 * program/period/division filters traverse them through {@code EXISTS}; the
	 * division filter additionally reaches the program through its config.
	 * {@code Person} is the shared-kernel table keyed by the bare {@code
	 * person_id}: the free-text branch matches folio, CURP, both surnames and the
	 * first name, so one box answers "la busco por folio o por nombre".
	 *
	 * <p>The {@code :search} comparisons fold case with {@code LOWER(...)} on
	 * both sides and interpolate the {@code %} wildcards inside SQL via
	 * {@code CONCAT} rather than inside the bound value, so the parameter is
	 * always bound as a plain string (no SQL injection surface) and callers keep
	 * passing the raw term. Blank is normalised to {@code null} before binding.
	 */
	@Query("""
			SELECT c FROM Candidate c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:programId IS NULL OR EXISTS (
			      SELECT cfg.id FROM ProgramAdmissionConfig cfg
			      WHERE cfg.id = c.admissionConfigId AND cfg.programId = :programId))
			  AND (:periodId IS NULL OR EXISTS (
			      SELECT cfg.id FROM ProgramAdmissionConfig cfg
			      WHERE cfg.id = c.admissionConfigId AND cfg.periodId = :periodId))
			  AND (:divisionId IS NULL OR EXISTS (
			      SELECT p.id FROM AcademicProgram p
			      WHERE p.divisionId = :divisionId
			        AND p.id = (SELECT cfg.programId FROM ProgramAdmissionConfig cfg
			                    WHERE cfg.id = c.admissionConfigId)))
			  AND (:search IS NULL OR LOWER(c.folio) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR EXISTS (
			           SELECT pe.id FROM Person pe
			           WHERE pe.id = c.personId
			             AND (LOWER(pe.curp) LIKE LOWER(CONCAT('%', :search, '%'))
			               OR LOWER(pe.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			               OR LOWER(pe.lastName1) LIKE LOWER(CONCAT('%', :search, '%'))
			               OR LOWER(pe.lastName2) LIKE LOWER(CONCAT('%', :search, '%')))))
			""")
	Page<Candidate> search(@Param("status") CandidateStatus status, @Param("programId") UUID programId,
			@Param("periodId") UUID periodId, @Param("divisionId") UUID divisionId, @Param("search") String search,
			Pageable pageable);
}