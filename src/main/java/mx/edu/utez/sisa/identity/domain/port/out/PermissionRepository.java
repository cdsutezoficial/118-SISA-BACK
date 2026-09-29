package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository {

	Permission save(Permission permission);

	Optional<Permission> findById(UUID id);

	Optional<Permission> findByKey(String key);

	List<Permission> findByIds(List<UUID> ids);

	boolean existsByKey(String key);

	boolean existsByKeyAndIdNot(String key, UUID id);

	PermissionSearchPage search(PermissionSearchCriteria criteria);

	record PermissionSearchCriteria(PermissionStatus status, String search, int page, int size) {
	}

	record PermissionSearchPage(List<Permission> content, long totalElements, int totalPages) {
	}
}