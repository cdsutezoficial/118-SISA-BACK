package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /users/{userId}/roles}: returns the created
 * grant plus the resolved role catalog data.
 */
public record AssignRoleResponse(UUID userRoleId, UUID roleId, String roleKey, String roleName, UUID divisionId) {
}
