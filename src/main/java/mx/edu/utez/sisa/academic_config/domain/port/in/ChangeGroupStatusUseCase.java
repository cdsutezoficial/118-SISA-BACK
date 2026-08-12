package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;

import java.util.UUID;

/**
 * Toggles a {@code Group}'s status between {@code OPEN} and {@code CLOSED} —
 * a simple 2-state toggle (NOT a sequential state machine), same shape as
 * {@code ChangeGenerationStatusUseCase}. Single interactor parameterized by
 * target status; both directions are always valid.
 */
public interface ChangeGroupStatusUseCase {

	GroupResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId reserved for future audit-log attribution; not consulted in this slice —
	 *                 mirrors {@code ChangeGenerationStatusUseCase.ChangeStatusCommand}
	 * @param groupId  the group whose status is transitioning
	 * @param target   the desired {@link GroupStatus} — {@code OPEN} or {@code CLOSED}
	 */
	record ChangeStatusCommand(UUID callerId, UUID groupId, GroupStatus target) {
	}
}
