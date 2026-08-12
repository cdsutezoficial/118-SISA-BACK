package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code Group}'s status between {@code OPEN} and {@code CLOSED}
 * (simple 2-state toggle — see {@link GroupStatus}). Single interactor
 * parameterized by target status, mirroring
 * {@code ChangeGenerationStatusUseCaseImpl}: both directions are always
 * valid, no sequence enforced.
 */
public class ChangeGroupStatusUseCaseImpl implements ChangeGroupStatusUseCase {

	private final GroupRepository groupRepository;

	public ChangeGroupStatusUseCaseImpl(GroupRepository groupRepository) {
		this.groupRepository = groupRepository;
	}

	@Override
	@Transactional
	public GroupResult changeStatus(ChangeStatusCommand command) {
		Group group = groupRepository.findById(command.groupId())
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + command.groupId()));

		if (command.target() == GroupStatus.OPEN) {
			group.open();
		}
		else {
			group.close();
		}
		Group saved = groupRepository.save(group);

		return CreateGroupUseCaseImpl.toResult(saved);
	}
}
