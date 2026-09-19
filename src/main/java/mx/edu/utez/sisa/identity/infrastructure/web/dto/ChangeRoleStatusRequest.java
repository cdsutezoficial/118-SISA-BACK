package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

public record ChangeRoleStatusRequest(@NotNull(message = "Selecciona el estatus del rol.") RoleStatus status) {
}