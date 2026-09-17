package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
		@NotBlank(message = "Escribe el nombre del rol.") String name,
		@NotBlank(message = "Escribe la clave del rol.") String key,
		@NotBlank(message = "Escribe una descripción para el rol.") String description) {
}