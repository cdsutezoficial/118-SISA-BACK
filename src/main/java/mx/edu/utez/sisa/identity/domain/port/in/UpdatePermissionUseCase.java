package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public interface UpdatePermissionUseCase {

	PermissionResult updatePermission(UpdatePermissionCommand command);

	record UpdatePermissionCommand(UUID permissionId, String name, String key) {
	}

	record PermissionResult(UUID id, String name, String key, PermissionStatus status) {
	}
}