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
	 * sellable, so the picker cannot offer a career the applicant will be refused
	 * for: {@code now} must be inside {@code opensAt}/{@code closesAt}, and the
	 * config must have an unclaimed slot left.
	 *
	 * <p>Both boundaries are inclusive, matching
	 * {@code RegisterCandidateUseCaseImpl}'s checks — the two have to agree or the
	 * picker starts lying the moment the clock passes a boundary.
	 *
	 * <p><b>The occupancy subquery below is a second copy of the rule in
	 * {@code AdmissionPaymentOccupancyQueries}, and it has to stay identical to
	 * it.</b> JPQL cannot call into a Spring Data fragment from inside another
	 * {@code @Query}, so the duplication is forced by the tool. It is not an
	 * oversight: if this subquery counted only PAID fichas while the claim counted
	 * claims as well, the picker would keep offering a career whose last slot was
	 * already being paid for, and the applicant would be refused at the checkout —
	 * the user-visible form of the overshoot this whole block exists to remove.
	 *
	 * <p>Both copies key the quota by {@code cand.admissionConfigId = c.id}. They
	 * disagreed once by a wider margin: this one counted per config while
	 * {@code AdmissionPaymentOccupancyQueries} counted per program, so a program
	 * whose old cycle was full had its new cycle offered here and refused at
	 * checkout. {@code ProgramAdmissionConfigOptionsQueryIT} holds the two
	 * definitions against the same data, on data that actually separates "per
	 * config" from "per program" — one cycle per program would pass either way.
	 *
	 * <p>Both copies also used to expire a claim by asking the payment catalog
	 * whether an admission concept was still payable, walking the program/level/
	 * general rate ladder on every read. That made the catalog's {@code
	 * availableUntil} the thing that decided when a checkout claim died, which is
	 * not what the applicant was promised and not what the checkout enforces. What
	 * releases a claim is now the ficha's own dates: still inside its
	 * {@code registeredAt} plazo ({@code :registeredNoLaterThan}) and inside this
	 * config's window ({@code :midnightToday}). Both cutoffs come from
	 * {@code FichaPaymentWindow}, so the copy here and the one in the admission
	 * module cannot drift apart on the arithmetic either.
	 *
	 * <p>{@code c.closesAt} is compared against {@code :midnightToday} even though
	 * {@code :now BETWEEN c.opensAt AND c.closesAt} above has already excluded
	 * anything past its window. It is kept to make the occupancy clause byte for
	 * byte the same rule the claim applies, which is the only thing that makes the
	 * duplication above maintainable.
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
			           AND (pay.paymentStatus = mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PAID
			                OR (pay.paymentStatus = mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING
			                    AND pay.checkoutClaimedAt IS NOT NULL
			                    AND cand.registeredAt >= :registeredNoLaterThan
			                    AND c.closesAt >= :midnightToday))
			      ) < c.maxCandidates
			ORDER BY p.name
			""")
	List<ProgramAdmissionConfigOptionProjection> findOpenOfferedOptions(@Param("now") Instant now,
			@Param("registeredNoLaterThan") Instant registeredNoLaterThan,
			@Param("midnightToday") Instant midnightToday);

	/** Minimal projection for the public picker — {@code id}, program name (label) and modality. */
	interface ProgramAdmissionConfigOptionProjection {
		UUID getId();

		String getProgramName();

		ProgramModality getModality();
	}
}
