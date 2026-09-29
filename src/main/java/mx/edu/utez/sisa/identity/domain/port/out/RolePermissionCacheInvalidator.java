package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port that signals the authorization infrastructure that the effective
 * permission set of one or more roles changed and the in-memory permission
 * cache must be invalidated (roles-permisos.md §3.2). Implemented by an
 * adapter in {@code identity.infrastructure.security}; invoked by domain use
 * cases that mutate role-permission assignments or permission status without
 * the domain depending on any infrastructure type.
 */
public interface RolePermissionCacheInvalidator {

	/**
	 * Requests the permission cache to be rebuilt after the current
	 * transaction commits. Safe to call from an infra-data (write) use case:
	 * no-op semantics if the change was rolled back.
	 */
	void rolePermissionsChanged();
}