package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * One-method read-only lookup for the {@code AcademicPeriod} name, backing
 * {@link ProgramAdmissionConfigQueryAdapter}'s ficha projection (the
 * destination period the accepted candidates enroll into). Lives in
 * {@code admission} (not {@code academic_config}) for the same "own minimal
 * access" reason as {@code ProgramAdmissionConfigLookupJpaRepository}.
 */
public interface AcademicPeriodNameLookupJpaRepository extends JpaRepository<AcademicPeriod, UUID> {
}