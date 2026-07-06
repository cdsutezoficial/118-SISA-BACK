package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.shared.model.RoleType;

import java.util.UUID;

/**
 * Request body for {@code POST /users/{userId}/roles} (design.md — REST
 * endpoints). {@code divisionId} is required only for division-scoped roles
 * — enforced by {@code AssignRoleUseCaseImpl}, not by bean validation here.
 */
public record AssignRoleRequest(@NotNull RoleType roleType, UUID divisionId) {
}
