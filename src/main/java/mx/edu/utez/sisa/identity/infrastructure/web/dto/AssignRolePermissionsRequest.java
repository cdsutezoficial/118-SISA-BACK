package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignRolePermissionsRequest(
		@NotNull(message = "Selecciona los permisos que deseas asignar.")
		@NotEmpty(message = "Selecciona al menos un permiso.") List<UUID> permissionIds) {
}