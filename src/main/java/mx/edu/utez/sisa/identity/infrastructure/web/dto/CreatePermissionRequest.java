package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionRequest(
		@NotBlank(message = "Escribe el nombre del permiso.") String name,
		@NotBlank(message = "La clave es obligatoria.") String key) {
}