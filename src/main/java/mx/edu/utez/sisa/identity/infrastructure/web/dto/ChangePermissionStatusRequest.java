package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

public record ChangePermissionStatusRequest(
		@NotNull(message = "Selecciona el estatus del permiso.") PermissionStatus status) {
}