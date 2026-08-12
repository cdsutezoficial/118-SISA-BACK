package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.shared.model.RoleType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /users/{id}} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.3):
 * {@code {userId, personId, fullName, username, status, mustChangePassword,
 * lastLoginAt, createdAt, roles[]}}. Unlike {@link UserListItemResponse},
 * each {@link UserRoleDetailItem} carries its own {@code userRoleId} so the
 * detail screen can target a specific grant for
 * {@code DELETE /users/{userId}/roles/{userRoleId}}.
 */
public record UserDetailResponse(UUID userId, UUID personId, String fullName, String username, UserStatus status,
		boolean mustChangePassword, Instant lastLoginAt, Instant createdAt, List<UserRoleDetailItem> roles) {

	public record UserRoleDetailItem(UUID userRoleId, RoleType roleType, UUID divisionId) {
	}
}
