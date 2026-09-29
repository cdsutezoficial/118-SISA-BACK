package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionCacheInvalidator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Infrastructure adapter for {@link RolePermissionCacheInvalidator}: defers
 * the {@link PermissionCache} rebuild until AFTER the current transaction
 * commits. The {@code AssignPermissionsToRoleUseCaseImpl} write path is
 * {@code @Transactional} — {@code deleteByRoleId} + {@code saveAll} are not
 * visible to other transactions until commit — so reloading in
 * {@link TransactionSynchronization#afterCommit()} guarantees the cache never
 * reflects a half-applied change. When no transaction is active (e.g. an
 * infra-data read path turned write), it reloads immediately.
 */
@Component
public class PermissionCacheInvalidator implements RolePermissionCacheInvalidator {

	private final PermissionCache permissionCache;

	public PermissionCacheInvalidator(PermissionCache permissionCache) {
		this.permissionCache = permissionCache;
	}

	@Override
	public void rolePermissionsChanged() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					permissionCache.reload();
				}
			});
		}
		else {
			permissionCache.reload();
		}
	}
}