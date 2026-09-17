package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePermissionRequest(
		@NotBlank(message = "Escribe el nombre del permiso.") String name,
		@NotBlank(message = "Escribe la clave del permiso.") String key) {
}