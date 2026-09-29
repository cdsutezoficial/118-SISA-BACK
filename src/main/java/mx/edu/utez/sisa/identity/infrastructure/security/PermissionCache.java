package mx.edu.utez.sisa.identity.infrastructure.security;

import jakarta.annotation.PostConstruct;
import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;
import mx.edu.utez.sisa.identity.infrastructure.persistence.PermissionJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.persistence.RoleJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.persistence.RolePermissionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory permission cache (roles-permisos.md §3.2): the JWT carries only
 * role keys, so per request this cache resolves the role keys the filter
 * extracted from the token into their union of permission keys — no database
 * query happens on the hot path. It is loaded once at startup by
 * {@link #reload()} and rebuilt in full whenever {@link
 * RolePermissionCacheInvalidator} fires after a role's permissions are
 * replaced ({@code PUT /roles/{id}/permissions}) or a permission is
 * deactivated/reactivated.
 * <p>
 * Only {@link RoleStatus#ACTIVE} roles and {@link PermissionStatus#ACTIVE}
 * permissions participate: an inactive role grants nothing, and a
 * deactivated permission disappears from every role that references it.
 * A multi-role user exercises the union of each role's key set (§3.2).
 */
@Component
public class PermissionCache {

	private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

	private final RoleJpaRepository roleRepository;

	private final PermissionJpaRepository permissionRepository;

	private final RolePermissionJpaRepository rolePermissionRepository;

	private volatile Map<String, Set<String>> permissionKeysByRoleKey = Map.of();

	public PermissionCache(RoleJpaRepository roleRepository, PermissionJpaRepository permissionRepository,
			RolePermissionJpaRepository rolePermissionRepository) {
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.rolePermissionRepository = rolePermissionRepository;
	}

	@PostConstruct
	public void reload() {
		Map<UUID, Permission> permissionById = new HashMap<>();
		for (Permission permission : permissionRepository.findAll()) {
			if (permission.getStatus() == PermissionStatus.ACTIVE) {
				permissionById.put(permission.getId(), permission);
			}
		}

		Map<UUID, String> roleKeyById = new HashMap<>();
		List<Role> roles = roleRepository.findAll();
		for (Role role : roles) {
			if (role.getStatus() == RoleStatus.ACTIVE) {
				roleKeyById.put(role.getId(), role.getKey());
			}
		}

		Map<String, Set<String>> permissionsByRoleKey = new HashMap<>();
		for (RolePermission rolePermission : rolePermissionRepository.findAll()) {
			String roleKey = roleKeyById.get(rolePermission.getRoleId());
			Permission permission = permissionById.get(rolePermission.getPermissionId());
			if (roleKey == null || permission == null) {
				continue;
			}
			permissionsByRoleKey.computeIfAbsent(roleKey, ignored -> new HashSet<>()).add(permission.getKey());
		}

		this.permissionKeysByRoleKey = immutableCopy(permissionsByRoleKey);
		log.info("Permission cache reloaded: {} active roles, {} permissions in memory", roleKeyById.size(),
				countPermissions(permissionKeysByRoleKey));
	}

	/**
	 * Union of the permission keys held by the given role keys (users carry
	 * role keys in the JWT, never permission keys — §3.2). Never returns
	 * {@code null}.
	 */
	public Set<String> permissionKeysFor(Collection<String> roleKeys) {
		Set<String> union = new HashSet<>();
		if (roleKeys == null) {
			return union;
		}
		for (String roleKey : roleKeys) {
			Set<String> keys = permissionKeysByRoleKey.get(roleKey);
			if (keys != null) {
				union.addAll(keys);
			}
		}
		return union;
	}

	private static Map<String, Set<String>> immutableCopy(Map<String, Set<String>> source) {
		Map<String, Set<String>> copy = new HashMap<>();
		source.forEach((roleKey, keys) -> copy.put(roleKey, Set.copyOf(keys)));
		return Map.copyOf(copy);
	}

	private static int countPermissions(Map<String, Set<String>> permissionsByRoleKey) {
		return permissionsByRoleKey.values().stream().mapToInt(Set::size).sum();
	}
}