package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.UUID;

public interface GetPermissionUseCase {

	PermissionResult getById(UUID permissionId);

	record PermissionResult(UUID id, String name, String key, PermissionStatus status) {
	}
}