package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.shared.model.RoleType;

import java.util.UUID;

/**
 * Response body for {@code POST /users/{userId}/roles} (design.md — REST
 * endpoints): {@code {userRoleId, roleType, divisionId}}.
 */
public record AssignRoleResponse(UUID userRoleId, RoleType roleType, UUID divisionId) {
}
