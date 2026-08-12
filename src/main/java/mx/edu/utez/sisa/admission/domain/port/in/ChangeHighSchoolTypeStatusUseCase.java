package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;

import java.util.UUID;

/**
 * Toggles a {@code HighSchoolType}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeOutreachChannelStatusUseCase}.
 */
public interface ChangeHighSchoolTypeStatusUseCase {

	HighSchoolTypeResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId       reserved for future audit-log attribution; not consulted in this slice —
	 *                       mirrors {@code ChangeOutreachChannelStatusUseCase.ChangeStatusCommand}
	 * @param highSchoolTypeId the type whose status is transitioning
	 * @param target         the desired {@link HighSchoolTypeStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID highSchoolTypeId, HighSchoolTypeStatus target) {
	}
}
