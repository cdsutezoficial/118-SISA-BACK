package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePermissionStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionCacheInvalidator;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.PermissionNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;

public class ChangePermissionStatusUseCaseImpl implements ChangePermissionStatusUseCase {

	private final UserRepository userRepository;

	private final PermissionRepository permissionRepository;

	private final RolePermissionCacheInvalidator rolePermissionCacheInvalidator;

	public ChangePermissionStatusUseCaseImpl(UserRepository userRepository, PermissionRepository permissionRepository,
			RolePermissionCacheInvalidator rolePermissionCacheInvalidator) {
		this.userRepository = userRepository;
		this.permissionRepository = permissionRepository;
		this.rolePermissionCacheInvalidator = rolePermissionCacheInvalidator;
	}

	@Override
	public PermissionResult changeStatus(ChangePermissionStatusCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		Permission permission = permissionRepository.findById(command.permissionId()).orElseThrow(
				() -> new PermissionNotFoundException("Permission not found: " + command.permissionId()));
		if (command.status() == PermissionStatus.ACTIVE) {
			permission.activate();
		}
		else {
			permission.deactivate();
		}

		Permission saved = permissionRepository.save(permission);
		rolePermissionCacheInvalidator.rolePermissionsChanged();
		return new PermissionResult(saved.getId(), saved.getName(), saved.getKey(), saved.getStatus());
	}
}