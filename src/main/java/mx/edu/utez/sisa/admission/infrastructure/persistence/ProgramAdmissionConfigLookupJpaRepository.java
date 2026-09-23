package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

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
}