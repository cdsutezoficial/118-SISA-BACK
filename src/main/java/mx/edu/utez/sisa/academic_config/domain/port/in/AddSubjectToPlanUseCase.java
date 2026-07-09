package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;

import java.util.UUID;

/**
 * Adds a {@code Subject} to a {@code PlanLevel} of an existing
 * {@code AcademicPlan} (spec: "Add and Update Subject"). Delegates to
 * {@code AcademicPlan.addSubject}, which rejects a {@code planLevelId}
 * belonging to a different plan (including a level from a different plan
 * entirely) and a duplicate {@code code} within the plan.
 * {@code classificationId} is accepted without existence validation in this
 * slice (spec: "classificationId is accepted without existence
 * validation").
 */
public interface AddSubjectToPlanUseCase {

	SubjectResult addSubject(AddSubjectCommand command);

	record AddSubjectCommand(UUID planId, UUID planLevelId, String code, String name, int credits, int weeklyHours,
			int evaluationUnits, int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
	}
}
