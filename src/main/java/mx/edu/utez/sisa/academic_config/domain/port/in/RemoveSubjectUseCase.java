package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.util.UUID;

/**
 * Removes a {@code Subject} owned by an {@code AcademicPlan} (spec: "Add and
 * Update Subject"). Delegates to {@code AcademicPlan.removeSubject}.
 */
public interface RemoveSubjectUseCase {

	void removeSubject(RemoveSubjectCommand command);

	record RemoveSubjectCommand(UUID planId, UUID subjectId) {
	}
}
