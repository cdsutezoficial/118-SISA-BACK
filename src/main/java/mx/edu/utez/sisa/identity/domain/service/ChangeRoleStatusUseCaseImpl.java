package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ChangeRoleStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;

public class ChangeRoleStatusUseCaseImpl implements ChangeRoleStatusUseCase {

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	public ChangeRoleStatusUseCaseImpl(UserRepository userRepository, RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
	}

	@Override
	public RoleResult changeStatus(ChangeRoleStatusCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId()));
		if (command.status() == mx.edu.utez.sisa.identity.domain.model.RoleStatus.ACTIVE) {
			role.activate();
		}
		else {
			role.deactivate();
		}

		Role saved = roleRepository.save(role);
		return new RoleResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus(), saved.getDescription());
	}
}