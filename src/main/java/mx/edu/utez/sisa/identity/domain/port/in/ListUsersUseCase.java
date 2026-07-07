package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.shared.model.RoleType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for registered users (01-identidad.md — Puertos
 * (in): "Consulta paginada de usuarios registrados", back-office "Usuarios"
 * screen). Restricted to ADMIN or SERVICIOS_ESCOLARES callers, enforced by
 * {@code SecurityFilterConfig}'s role matcher (not a domain-level role check,
 * matching how CreateUserUseCase/AssignRoleUseCase delegate role enforcement
 * to the web/security layer and only assert the caller's mustChangePassword
 * gate here).
 */
public interface ListUsersUseCase {

	ListUsersResult listUsers(ListUsersQuery query);

	/**
	 * @param callerId the acting user, used only for the mustChangePassword guard (role authorization is
	 *                 enforced by SecurityFilterConfig)
	 * @param roleType optional — matches users having at least one {@link UserRole} with this type
	 * @param status   optional — matches the user's current status
	 * @param search   optional free-text match against {@code username} or the linked Person's full name
	 * @param page     zero-based page index; negative values are normalized to 0
	 * @param size     page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListUsersQuery(UUID callerId, RoleType roleType, UserStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListUsersResult(List<UserSummary> users, long totalElements, int totalPages, int page, int size) {
	}

	record UserSummary(UUID userId, UUID personId, String fullName, String username, List<UserRoleSummary> roles,
			UserStatus status, Instant lastLoginAt) {
	}

	record UserRoleSummary(RoleType roleType, UUID divisionId) {
	}
}
