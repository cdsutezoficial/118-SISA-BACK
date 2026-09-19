package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.port.in.GetPermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.shared.exception.PermissionNotFoundException;

import java.util.UUID;

public class GetPermissionUseCaseImpl implements GetPermissionUseCase {

	private final PermissionRepository permissionRepository;

	public GetPermissionUseCaseImpl(PermissionRepository permissionRepository) {
		this.permissionRepository = permissionRepository;
	}

	@Override
	public PermissionResult getById(UUID permissionId) {
		Permission permission = permissionRepository.findById(permissionId)
				.orElseThrow(() -> new PermissionNotFoundException("Permission not found: " + permissionId));
		return new PermissionResult(permission.getId(), permission.getName(), permission.getKey(), permission.getStatus());
	}
}