package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.List;
import java.util.UUID;

public interface ListRolesUseCase {

	ListRolesResult listRoles(ListRolesQuery query);

	record ListRolesQuery(RoleStatus status, String search, int page, int size) {
		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListRolesResult(List<RoleSummary> items, long totalElements, int totalPages, int page, int size) {
	}

	record RoleSummary(UUID id, String name, String key, RoleStatus status, String description) {
	}
}