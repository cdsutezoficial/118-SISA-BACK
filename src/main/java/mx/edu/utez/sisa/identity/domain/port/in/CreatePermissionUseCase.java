package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public interface CreatePermissionUseCase {

	PermissionResult createPermission(CreatePermissionCommand command);

	record CreatePermissionCommand(String name, String key) {
	}

	record PermissionResult(UUID id, String name, String key, PermissionStatus status) {
	}
}