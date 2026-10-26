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
	 * {@code FichaOccupancyJpaRepository} (admission), and it has to stay
	 * identical to it.</b> JPQL cannot call into another repository from inside a
	 * {@code @Query}, so the duplication is forced by the tool. If this one counted
	 * only paid fichas while the checkout counted open attempts as well, the picker
	 * would keep offering a career whose last place is being paid right now, and
	 * the applicant would be refused at the checkout.
	 *
	 * <p>The rule (2026-10-23, no {@code admission_payment} table any more): a
	 * place is taken by a candidate of the config whose ficha charge
	 * ({@code finance.payment}, linked by {@code Candidate.fichaPaymentId}) is
	 * {@code PAID}/{@code EXEMPT}, or who has a {@code STARTED} checkout attempt
	 * opened at or after {@code :attemptsOpenedSince} (now − 15 min, see
 * {@code sisa.admission.payment.checkout-hold-minutes}). Keyed by config, not by program,
	 * so a full old cycle never blocks the same program's new one.
	 */
	@Query(value = """
			SELECT c.id AS id, p.name AS programName, p.modality AS modality
			FROM ProgramAdmissionConfig c
			JOIN AcademicProgram p ON p.id = c.programId
			WHERE c.status = mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus.OPEN
			  AND c.isOffered = true
			  AND :now BETWEEN c.opensAt AND c.closesAt
			  AND (SELECT COUNT(cand)
			         FROM Candidate cand
			         WHERE cand.admissionConfigId = c.id
			           AND cand.fichaPaymentId IS NOT NULL
			           AND (EXISTS (SELECT pay.id FROM Payment pay
			                        WHERE pay.id = cand.fichaPaymentId
			                          AND pay.status IN (mx.edu.utez.sisa.finance.domain.model.PaymentStatus.PAID,
			                                             mx.edu.utez.sisa.finance.domain.model.PaymentStatus.EXEMPT))
			                OR EXISTS (SELECT att.id FROM CheckoutAttempt att
			                           WHERE att.paymentId = cand.fichaPaymentId
			                             AND att.closeReason = mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason.STARTED
			                             AND att.createdAt >= :attemptsOpenedSince))
			      ) < c.maxCandidates
			ORDER BY p.name
			""")
	List<ProgramAdmissionConfigOptionProjection> findOpenOfferedOptions(@Param("now") Instant now,
			@Param("attemptsOpenedSince") Instant attemptsOpenedSince);

	/** Minimal projection for the public picker — {@code id}, program name (label) and modality. */
	interface ProgramAdmissionConfigOptionProjection {
		UUID getId();

		String getProgramName();

		ProgramModality getModality();
	}
}
