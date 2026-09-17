package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String key, RoleStatus status, String description,
		List<RolePermissionItemResponse> permissions) {

	public record RolePermissionItemResponse(UUID id, String name, String key,
			mx.edu.utez.sisa.identity.domain.model.PermissionStatus status) {
	}
}