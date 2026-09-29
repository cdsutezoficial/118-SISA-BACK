package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

	Role save(Role role);

	Optional<Role> findById(UUID id);

	Optional<Role> findByKey(String key);

	List<Role> findByIds(List<UUID> ids);

	boolean existsByKey(String key);

	boolean existsByKeyAndIdNot(String key, UUID id);

	RoleSearchPage search(RoleSearchCriteria criteria);

	record RoleSearchCriteria(RoleStatus status, String search, int page, int size) {
	}

	record RoleSearchPage(List<Role> content, long totalElements, int totalPages) {
	}
}