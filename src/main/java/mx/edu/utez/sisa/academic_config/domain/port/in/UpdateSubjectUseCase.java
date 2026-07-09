package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;

import java.util.UUID;

/**
 * Updates an existing {@code Subject} owned by an {@code AcademicPlan}
 * (spec: "Add and Update Subject"). Delegates to
 * {@code AcademicPlan.updateSubject}.
 */
public interface UpdateSubjectUseCase {

	SubjectResult updateSubject(UpdateSubjectCommand command);

	record UpdateSubjectCommand(UUID planId, UUID subjectId, String code, String name, int credits, int weeklyHours,
			int evaluationUnits, int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
	}
}
