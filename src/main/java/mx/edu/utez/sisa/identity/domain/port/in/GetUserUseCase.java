package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full-detail lookup for a single {@code User} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.3, backs
 * the "UsuarioDetalle" screen). Unlike {@code ListUsersUseCase}'s summary
 * rows, each {@link UserRoleDetail} exposes its own {@code userRoleId} — not
 * surfaced today by {@code GET /users} — so the detail screen can target a
 * specific role grant for {@code RevokeRoleUseCase}. Restricted to ADMIN or
 * SERVICIOS_ESCOLARES callers, same split of responsibilities as
 * {@code ListUsersUseCase}.
 */
public interface GetUserUseCase {

	UserDetailResult getUser(GetUserQuery query);

	/**
	 * @param callerId the acting user, used only for the mustChangePassword guard (role authorization is
	 *                 enforced by SecurityFilterConfig)
	 * @param userId   the target {@code User} to load
	 */
	record GetUserQuery(UUID callerId, UUID userId) {
	}

	record UserDetailResult(UUID userId, UUID personId, String fullName, String username, UserStatus status,
			boolean mustChangePassword, Instant lastLoginAt, Instant createdAt, List<UserRoleDetail> roles) {
	}

	record UserRoleDetail(UUID userRoleId, UUID roleId, String roleKey, String roleName, UUID divisionId) {
	}
}
