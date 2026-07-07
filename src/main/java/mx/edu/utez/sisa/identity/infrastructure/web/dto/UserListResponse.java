package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /users} (01-identidad.md — ListUsersUseCase):
 * {@code {items[], totalElements, totalPages, page, size}}.
 */
public record UserListResponse(List<UserListItemResponse> items, long totalElements, int totalPages, int page,
		int size) {
}
