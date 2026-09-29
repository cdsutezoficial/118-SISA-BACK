package mx.edu.utez.sisa.identity.domain.port.in;

import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.List;
import java.util.UUID;

public interface ListPermissionsUseCase {

	ListPermissionsResult listPermissions(ListPermissionsQuery query);

	record ListPermissionsQuery(PermissionStatus status, String search, int page, int size) {
		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListPermissionsResult(List<PermissionSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	record PermissionSummary(UUID id, String name, String key, PermissionStatus status) {
	}
}