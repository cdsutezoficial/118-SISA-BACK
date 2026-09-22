package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RolePermission;
import mx.edu.utez.sisa.identity.infrastructure.persistence.PermissionJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.persistence.RoleJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.persistence.RolePermissionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies roles-permisos.md §3.2: {@link PermissionCache} resolves JWT role
 * keys into permission-key unions, reload rebuilds the map, and only active
 * roles/permissions participate.
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheTest {

	private static final UUID SERVICIOS_ESCOLARES_ID = UUID.randomUUID();
	private static final UUID PERSONAL_FINANZAS_ID = UUID.randomUUID();
	private static final UUID INACTIVE_ROLE_ID = UUID.randomUUID();
	private static final UUID USERS_READ_ID = UUID.randomUUID();
	private static final UUID PAYMENT_CONCEPTS_READ_ID = UUID.randomUUID();
	private static final UUID ARCHIVED_PERMISSION_ID = UUID.randomUUID();

	@Mock
	private RoleJpaRepository roleRepository;
	@Mock
	private PermissionJpaRepository permissionRepository;
	@Mock
	private RolePermissionJpaRepository rolePermissionRepository;

	@Test
	void resolvesUnionOfRoleKeyPermissionSets() {
		when(roleRepository.findAll()).thenReturn(List.of(role("SERVICIOS_ESCOLARES", SERVICIOS_ESCOLARES_ID),
				role("PERSONAL_FINANZAS", PERSONAL_FINANZAS_ID)));
		when(permissionRepository.findAll())
				.thenReturn(List.of(permission("USERS_READ", USERS_READ_ID),
						permission("PAYMENT_CONCEPTS_READ", PAYMENT_CONCEPTS_READ_ID)));
		when(rolePermissionRepository.findAll())
				.thenReturn(List.of(new RolePermission(SERVICIOS_ESCOLARES_ID, USERS_READ_ID),
						new RolePermission(PERSONAL_FINANZAS_ID, PAYMENT_CONCEPTS_READ_ID)));

		PermissionCache cache = new PermissionCache(roleRepository, permissionRepository, rolePermissionRepository);
		cache.reload();

		assertThat(cache.permissionKeysFor(List.of("SERVICIOS_ESCOLARES", "PERSONAL_FINANZAS")))
				.containsExactlyInAnyOrder("USERS_READ", "PAYMENT_CONCEPTS_READ");
		assertThat(cache.permissionKeysFor(List.of("SERVICIOS_ESCOLARES"))).containsExactly("USERS_READ");
		assertThat(cache.permissionKeysFor(List.of("DOCENTE"))).isEmpty();
	}

	@Test
	void excludesInactiveRolesAndPermissions() {
		Permission archived = permission("ARCHIVED", ARCHIVED_PERMISSION_ID);
		archived.deactivate();
		Role oldRole = role("OLD_ROLE", INACTIVE_ROLE_ID);
		oldRole.deactivate();
		when(roleRepository.findAll()).thenReturn(List.of(role("SERVICIOS_ESCOLARES", SERVICIOS_ESCOLARES_ID), oldRole));
		when(permissionRepository.findAll()).thenReturn(List.of(permission("USERS_READ", USERS_READ_ID), archived));
		when(rolePermissionRepository.findAll())
				.thenReturn(List.of(new RolePermission(SERVICIOS_ESCOLARES_ID, USERS_READ_ID),
						new RolePermission(SERVICIOS_ESCOLARES_ID, ARCHIVED_PERMISSION_ID),
						new RolePermission(INACTIVE_ROLE_ID, USERS_READ_ID)));

		PermissionCache cache = new PermissionCache(roleRepository, permissionRepository, rolePermissionRepository);
		cache.reload();

		assertThat(cache.permissionKeysFor(List.of("SERVICIOS_ESCOLARES"))).containsExactly("USERS_READ");
		assertThat(cache.permissionKeysFor(List.of("OLD_ROLE"))).isEmpty();
	}

	@Test
	void reloadMimickingInvalidationPicksUpChangedAssignments() {
		when(roleRepository.findAll()).thenReturn(List.of(role("SERVICIOS_ESCOLARES", SERVICIOS_ESCOLARES_ID)));
		when(permissionRepository.findAll()).thenReturn(List.of(permission("USERS_READ", USERS_READ_ID)));
		when(rolePermissionRepository.findAll())
				.thenReturn(List.of(new RolePermission(SERVICIOS_ESCOLARES_ID, USERS_READ_ID)));

		PermissionCache cache = new PermissionCache(roleRepository, permissionRepository, rolePermissionRepository);
		cache.reload();
		assertThat(cache.permissionKeysFor(List.of("SERVICIOS_ESCOLARES"))).containsExactly("USERS_READ");

		when(rolePermissionRepository.findAll()).thenReturn(List.of());

		cache.reload();

		assertThat(cache.permissionKeysFor(List.of("SERVICIOS_ESCOLARES"))).isEmpty();
	}

	private static Role role(String key, UUID id) {
		Role role = new Role("Role " + key, key, "description");
		ReflectionTestUtils.setField(role, "id", id);
		return role;
	}

	private static Permission permission(String key, UUID id) {
		Permission permission = new Permission(key, key);
		ReflectionTestUtils.setField(permission, "id", id);
		return permission;
	}
}