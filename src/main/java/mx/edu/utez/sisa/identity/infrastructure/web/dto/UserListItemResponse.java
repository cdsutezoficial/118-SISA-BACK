package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A single row of {@code GET /users} (01-identidad.md — ListUsersUseCase):
 * {@code {userId, personId, fullName, username, roles[], status, lastLoginAt}}.
 */
public record UserListItemResponse(UUID userId, UUID personId, String fullName, String username,
		List<UserRoleItem> roles, UserStatus status, Instant lastLoginAt) {

	public record UserRoleItem(UUID roleId, String roleKey, String roleName, UUID divisionId) {
	}
}
