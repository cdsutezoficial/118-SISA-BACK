package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the (HTTP method, Ant pattern) → permission-key mapping, whose two
 * failure modes are silent: a pattern that matches nothing leaves the route
 * governed only by the coarse role matchers, and a broad pattern declared
 * before a narrow one shadows it (roles-permisos.md §3.3 — first match wins).
 */
class PermissionRegistryTest {

	private static final String USER_ID = "8f14e45f-ceea-467a-9ba9-1c1b1a3f4e2d";

	@Test
	void adminPasswordResetResolvesToItsOwnPermission() {
		assertThat(PermissionRegistry.resolve("POST", "/users/" + USER_ID + "/reset-password"))
				.contains("USERS_RESET_PASSWORD");
	}

	@Test
	void adminPasswordResetIsMatchedForAnyUserId() {
		assertThat(PermissionRegistry.resolve("POST", "/users/" + UUID.randomUUID() + "/reset-password"))
				.contains("USERS_RESET_PASSWORD");
	}

	@Test
	void narrowerUserEntriesAreNotShadowedByTheirBroaderSiblings() {
		assertThat(PermissionRegistry.resolve("POST", "/users")).contains("USERS_CREATE");
		assertThat(PermissionRegistry.resolve("POST", "/users/" + USER_ID + "/roles"))
				.contains("USERS_ASSIGN_ROLE");
		assertThat(PermissionRegistry.resolve("DELETE", "/users/" + USER_ID + "/roles/" + USER_ID))
				.contains("USERS_REVOKE_ROLE");
		assertThat(PermissionRegistry.resolve("PATCH", "/users/" + USER_ID + "/unlock"))
				.contains("USERS_UNLOCK");
	}

	@Test
	void passwordResetDoesNotGrantReadOnTheUserResource() {
		assertThat(PermissionRegistry.resolve("GET", "/users/" + USER_ID + "/reset-password"))
				.contains("USERS_READ");
	}
}