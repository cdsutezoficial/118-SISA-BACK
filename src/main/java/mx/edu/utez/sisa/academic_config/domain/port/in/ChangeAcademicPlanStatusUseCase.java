package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;

import java.util.UUID;

/**
 * Toggles an {@code AcademicPlan}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Plan"). Single
 * interactor parameterized by target status, mirroring
 * {@code ChangeAcademicProgramStatusUseCase}. Deactivating one plan MUST NOT
 * affect any other plan under the same {@code programId} — multiple
 * {@code ACTIVE} plans may coexist per program (spec: "toggling one is
 * independent of the others").
 */
public interface ChangeAcademicPlanStatusUseCase {

	AcademicPlanResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId reserved for future audit-log attribution; not consulted in this slice — mirrors
	 *                 {@code ChangeAcademicProgramStatusUseCase.ChangeStatusCommand}
	 * @param planId   the plan whose status is transitioning
	 * @param target   the desired {@link PlanStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID planId, PlanStatus target) {
	}
}
