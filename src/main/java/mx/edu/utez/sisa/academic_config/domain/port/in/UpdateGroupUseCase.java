package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Updates an existing {@code Group}'s FK associations and catalog fields
 * (PUT {@code /groups/{id}}). Applies the same {@code generationId}/
 * {@code periodId}/{@code planLevelId} existence and membership rules as
 * creation. {@code status} is deliberately absent — status transitions are
 * the sole responsibility of {@code ChangeGroupStatusUseCase}.
 */
public interface UpdateGroupUseCase {

	GroupResult updateGroup(UpdateGroupCommand command);

	record UpdateGroupCommand(UUID groupId, UUID generationId, UUID periodId, UUID planLevelId, String code,
			int maxCapacity, Shift shift) {
	}
}
