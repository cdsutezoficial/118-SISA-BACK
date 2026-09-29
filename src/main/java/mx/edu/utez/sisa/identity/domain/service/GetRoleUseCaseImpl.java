package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.port.in.GetRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GetRoleUseCaseImpl implements GetRoleUseCase {

	private final RoleRepository roleRepository;

	private final RolePermissionRepository rolePermissionRepository;

	private final PermissionRepository permissionRepository;

	public GetRoleUseCaseImpl(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository,
			PermissionRepository permissionRepository) {
		this.roleRepository = roleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.permissionRepository = permissionRepository;
	}

	@Override
	public RoleDetailResult getById(UUID roleId) {
		Role role = roleRepository.findById(roleId)
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));

		List<UUID> permissionIds = rolePermissionRepository.findByRoleId(roleId).stream().map(rp -> rp.getPermissionId()).toList();
		Map<UUID, Permission> permissionsById = permissionRepository.findByIds(permissionIds).stream()
				.collect(Collectors.toMap(Permission::getId, Function.identity()));

		List<RolePermissionSummary> permissions = permissionIds.stream().map(permissionsById::get)
				.filter(permission -> permission != null)
				.map(permission -> new RolePermissionSummary(permission.getId(), permission.getName(), permission.getKey(),
						permission.getStatus()))
				.toList();

		return new RoleDetailResult(role.getId(), role.getName(), role.getKey(), role.getStatus(),
				role.getDescription(), permissions);
	}
}