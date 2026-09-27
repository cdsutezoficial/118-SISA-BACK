package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

	/**
	 * Reference-catalog read backing {@code GET /program-admission-configs/options}
	 * (plan: {@code docs/plans/sisa-candidate-ficha.md} — the public ficha
	 * wizard's program picker). Returns only {@link ProgramAdmissionConfigStatus#OPEN}
	 * AND {@code isOffered} configs, joined lazily against their
	 * {@code AcademicProgram} (a bare {@code UUID} column, no JPA relation) so
	 * the picker can label a config by program name + modality. Interface
	 * projection keeps the read light (glide-light reads, same convention as
	 * {@code ProgramOptionProjection}).
	 *
	 * <p>It also applies the two rules that decide whether a config is actually
	 * sellable, so the picker cannot offer a career the registration endpoint is
	 * about to reject: {@code now} must be inside {@code opensAt}/{@code closesAt},
	 * and fewer than {@code maxCandidates} fichas may be paid. Without them the
	 * applicant picks a career, fills four steps, and only then reads why it failed.
	 *
	 * <p>Both boundaries are inclusive, matching
	 * {@code RegisterCandidateUseCaseImpl}'s checks — the two have to agree or the
	 * picker starts lying the moment the clock passes a boundary.
	 *
	 * <p>The quota subquery reaches into {@code admission}'s tables on purpose: the
	 * rule is "paid fichas &lt; quota", and only a query over {@code Candidate} +
	 * {@code AdmissionPayment} can answer the paid half. It is read-only and stays
	 * here because this repository already backs the public picker's query, so
	 * moving it would mean a second round trip on the page's first request.
	 */
	@Query(value = """
			SELECT c.id AS id, p.name AS programName, p.modality AS modality
			FROM ProgramAdmissionConfig c
			JOIN AcademicProgram p ON p.id = c.programId
			WHERE c.status = mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus.OPEN
			  AND c.isOffered = true
			  AND :now BETWEEN c.opensAt AND c.closesAt
			  AND (SELECT COUNT(pay)
			         FROM Candidate cand
			         JOIN AdmissionPayment pay ON pay.candidateId = cand.id
			         WHERE cand.admissionConfigId = c.id
			           AND pay.paymentStatus = mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PAID
			      ) < c.maxCandidates
			ORDER BY p.name
			""")
	List<ProgramAdmissionConfigOptionProjection> findOpenOfferedOptions(@Param("now") Instant now);

	/** Minimal projection for the public picker — {@code id}, program name (label) and modality. */
	interface ProgramAdmissionConfigOptionProjection {
		UUID getId();

		String getProgramName();

		ProgramModality getModality();
	}
}
