package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data read-side helper for {@code Subject} counts. There is
 * deliberately no {@code SubjectRepository} out-port — every mutation goes
 * through {@code AcademicPlan}'s mutator methods (see
 * {@link Subject}'s javadoc). This interface exists only to back the
 * {@code GET /config-academica/statistics} dashboard counter via the
 * inherited {@link JpaRepository#count()}, so the read model does not have to
 * reach into the plan aggregate through the domain layer.
 */
public interface SubjectJpaRepository extends JpaRepository<Subject, UUID> {
}