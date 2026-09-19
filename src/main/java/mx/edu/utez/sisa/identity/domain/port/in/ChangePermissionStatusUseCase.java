package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public interface ChangePermissionStatusUseCase {

	PermissionResult changeStatus(ChangePermissionStatusCommand command);

	record ChangePermissionStatusCommand(UUID callerId, UUID permissionId, PermissionStatus status) {
	}

	record PermissionResult(UUID id, String name, String key, PermissionStatus status) {
	}
}