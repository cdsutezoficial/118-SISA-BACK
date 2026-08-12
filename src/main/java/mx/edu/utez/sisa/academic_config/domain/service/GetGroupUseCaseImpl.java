package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code Group} by id, 404 if missing — same pattern as
 * {@code GetGenerationUseCaseImpl}.
 */
public class GetGroupUseCaseImpl implements GetGroupUseCase {

	private final GroupRepository groupRepository;

	public GetGroupUseCaseImpl(GroupRepository groupRepository) {
		this.groupRepository = groupRepository;
	}

	@Override
	public GroupResult getById(UUID id) {
		Group group = groupRepository.findById(id).orElseThrow(() -> new GroupNotFoundException("Group not found: " + id));
		return CreateGroupUseCaseImpl.toResult(group);
	}
}
