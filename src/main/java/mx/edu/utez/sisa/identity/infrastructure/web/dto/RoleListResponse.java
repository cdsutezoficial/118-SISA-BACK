package mx.edu.utez.sisa.identity.infrastructure.web.dto;

import java.util.List;

public record RoleListResponse(List<RoleListItemResponse> items, long totalElements, int totalPages, int page,
		int size) {
}