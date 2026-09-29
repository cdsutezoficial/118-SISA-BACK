package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link ProgramAdmissionConfigQueryAdapter},
 * over the {@code program_admission_config} table. Deliberately a separate
 * interface from {@code academic_config.ProgramAdmissionConfigJpaRepository} —
 * {@code admission} defines its own minimal read-only repository over the
 * cross-bounded-context aggregate instead of importing academic_config's
 * full repository (same "own minimal access" rationale as
 * {@code CandidatePersonJpaRepository} / academic_config's
 * {@code PersonLookupJpaRepository}).
 */
public interface ProgramAdmissionConfigLookupJpaRepository extends JpaRepository<ProgramAdmissionConfig, UUID> {

	/**
	 * Loads the config taking a row-level write lock for the rest of the
	 * transaction, so two candidates claiming the last slot of the same career
	 * are serialised on this row instead of both reading the same count.
	 *
	 * <p>This is the whole concurrency argument, and it is deliberately narrow:
	 * the lock is held only across <em>count, compare, claim, commit</em> — never
	 * across the gateway call. Holding it during the HTTP round-trip would turn a
	 * slow payment provider into a stalled admission process, and committing
	 * before the call is what makes the claim durable while the lock is free.
	 *
	 * <p>Being a row lock and not a table lock, a claim on one career never waits
	 * on another.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT cfg FROM ProgramAdmissionConfig cfg WHERE cfg.id = :id")
	Optional<ProgramAdmissionConfig> findByIdForUpdate(@Param("id") UUID id);
}
