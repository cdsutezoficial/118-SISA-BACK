package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository.PermissionSearchCriteria;

public class ListPermissionsUseCaseImpl implements ListPermissionsUseCase {

	private final PermissionRepository permissionRepository;

	public ListPermissionsUseCaseImpl(PermissionRepository permissionRepository) {
		this.permissionRepository = permissionRepository;
	}

	@Override
	public ListPermissionsResult listPermissions(ListPermissionsQuery query) {
		PermissionSearchCriteria criteria = new PermissionSearchCriteria(query.status(), query.search(),
				normalizePage(query.page()), normalizeSize(query.size()));
		var page = permissionRepository.search(criteria);
		return new ListPermissionsResult(page.content().stream()
				.map(permission -> new PermissionSummary(permission.getId(), permission.getName(), permission.getKey(),
						permission.getStatus()))
				.toList(), page.totalElements(), page.totalPages(), criteria.page(), criteria.size());
	}

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListPermissionsQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListPermissionsQuery.MAX_PAGE_SIZE);
	}
}