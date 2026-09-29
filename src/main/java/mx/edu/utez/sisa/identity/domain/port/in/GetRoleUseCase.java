package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.List;
import java.util.UUID;

public interface GetRoleUseCase {

	RoleDetailResult getById(UUID roleId);

	record RoleDetailResult(UUID id, String name, String key, RoleStatus status, String description,
			List<RolePermissionSummary> permissions) {
	}

	record RolePermissionSummary(UUID id, String name, String key, PermissionStatus status) {
	}
}