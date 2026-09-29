package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository.RoleSearchCriteria;

public class ListRolesUseCaseImpl implements ListRolesUseCase {

	private final RoleRepository roleRepository;

	public ListRolesUseCaseImpl(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	@Override
	public ListRolesResult listRoles(ListRolesQuery query) {
		RoleSearchCriteria criteria = new RoleSearchCriteria(query.status(), query.search(), normalizePage(query.page()),
				normalizeSize(query.size()));
		var page = roleRepository.search(criteria);
		return new ListRolesResult(page.content().stream()
				.map(role -> new RoleSummary(role.getId(), role.getName(), role.getKey(), role.getStatus(),
						role.getDescription()))
				.toList(), page.totalElements(), page.totalPages(), criteria.page(), criteria.size());
	}

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListRolesQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListRolesQuery.MAX_PAGE_SIZE);
	}
}