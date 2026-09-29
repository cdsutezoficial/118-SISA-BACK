package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * One-method read-only lookup for the {@code AcademicProgram} name, backing
 * {@link ProgramAdmissionConfigQueryAdapter}'s ficha projection. Lives in
 * {@code admission} (not {@code academic_config}) for the same "own minimal
 * access" reason as {@code ProgramAdmissionConfigLookupJpaRepository}: the
 * admission bounded context needs only the program's display name to render
 * the ficha / emails / PDF, so it keeps a tiny repository instead of
 * importing academic_config's full {@code AcademicProgram} repository.
 */
public interface AcademicProgramNameLookupJpaRepository extends JpaRepository<AcademicProgram, UUID> {
}