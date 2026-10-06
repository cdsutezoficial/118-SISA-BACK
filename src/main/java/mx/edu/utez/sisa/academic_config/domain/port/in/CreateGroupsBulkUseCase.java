package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.List;
import java.util.UUID;

/**
 * Bulk group creation — {@code POST /groups/bulk}.
 *
 * <p>
 * Exists because registering N groups one POST at a time is both slow and
 * wrong: the letters have to be allocated against the same "next free" state,
 * and a partial failure would leave the generation with a ragged set. Here the
 * whole batch is one transaction, so either every group lands or none does.
 *
 * <p>
 * {@code code} is absent from the command on purpose — it is computed from
 * {@code planLevelId}'s level number plus the first free letters, per
 * {@code GroupCodeSequence}. A caller-supplied code would defeat the point.
 * {@code programId} is absent for the same reason as in
 * {@code CreateGroupRequest}: resolved server-side from the generation.
 */
public interface CreateGroupsBulkUseCase {

	/**
	 * @param generationId required — existing generation the groups belong to
	 * @param periodId     required — existing period the groups run in
	 * @param planLevelId  required — level of the generation's plan; fixes the
	 *                     numeric prefix of every generated code
	 * @param quantity     required — how many groups to create, 1..26 (A–Z)
	 * @param maxCapacity  required — shared by every group in the batch, at
	 *                     least 1
	 * @param shift        required — shared by every group in the batch
	 */
	record CreateGroupsBulkCommand(UUID generationId, UUID periodId, UUID planLevelId, int quantity, int maxCapacity,
			Shift shift) {
	}

	/**
	 * @param created the groups actually persisted, in the order they were
	 *                created, so the UI can list the letters it just used
	 */
	record CreateGroupsBulkResult(List<GroupResult> created) {
	}

	CreateGroupsBulkResult createGroupsBulk(CreateGroupsBulkCommand command);
}