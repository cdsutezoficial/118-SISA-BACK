package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.AssignPermissionsToRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionCacheInvalidator;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.PermissionNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AssignPermissionsToRoleUseCaseImpl implements AssignPermissionsToRoleUseCase {

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	private final RolePermissionRepository rolePermissionRepository;

	private final RolePermissionCacheInvalidator rolePermissionCacheInvalidator;

	public AssignPermissionsToRoleUseCaseImpl(UserRepository userRepository, RoleRepository roleRepository,
			PermissionRepository permissionRepository, RolePermissionRepository rolePermissionRepository,
			RolePermissionCacheInvalidator rolePermissionCacheInvalidator) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.rolePermissionCacheInvalidator = rolePermissionCacheInvalidator;
	}

	@Override
	@Transactional
	public RolePermissionsResult assignPermissions(AssignPermissionsCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId()));

		List<UUID> requestedIds = List.copyOf(new LinkedHashSet<>(command.permissionIds()));
		List<Permission> permissions = permissionRepository.findByIds(requestedIds);
		if (permissions.size() != requestedIds.size()) {
			throw new PermissionNotFoundException("Uno o más permisos seleccionados ya no están disponibles.");
		}

		rolePermissionRepository.deleteByRoleId(role.getId());
		rolePermissionRepository.saveAll(requestedIds.stream().map(permissionId -> new RolePermission(role.getId(), permissionId)).toList());
		rolePermissionCacheInvalidator.rolePermissionsChanged();

		Map<UUID, Permission> permissionsById = permissions.stream()
				.collect(Collectors.toMap(Permission::getId, Function.identity()));
		List<RolePermissionSummary> summaries = requestedIds.stream().map(permissionsById::get)
				.map(permission -> new RolePermissionSummary(permission.getId(), permission.getName(), permission.getKey(),
						permission.getStatus()))
				.toList();

		return new RolePermissionsResult(role.getId(), role.getName(), role.getKey(), role.getStatus(),
				role.getDescription(), summaries);
	}
}