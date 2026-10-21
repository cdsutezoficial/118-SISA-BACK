package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the (HTTP method, Ant pattern) → permission-key mapping, whose failure
 * modes are silent: a pattern that matches nothing leaves the route governed
 * only by the coarse role matchers, a broad pattern declared before a narrow one
 * shadows it (roles-permisos.md §3.3 — first match wins), and an exact path can
 * be swallowed by its own nested sibling ("/candidates/*" does NOT cover
 * "/candidates"). Each of those was a real bug here, so each is pinned below.
 */
class PermissionRegistryTest {

	private static final String USER_ID = "8f14e45f-ceea-467a-9ba9-1c1b1a3f4e2d";

	// ── candidate list ────────────────────────────────────────────────────────

	@Test
	void candidateListMapsToCandidatesRead() {
		assertThat(PermissionRegistry.resolve("GET", "/candidates")).contains("CANDIDATES_READ");
	}

	@Test
	void candidateDetailStaysUnregisteredBecauseItIsPublic() {
		assertThat(PermissionRegistry.resolve("GET", "/candidates/2f1c0e2e-0000-0000-0000-000000000000")).isEmpty();
	}

	// ── matching rules ────────────────────────────────────────────────────────

	@Test
	void referenceClassPathsAreNeverGovernedByTheFineGrainedLayer() {
		assertThat(PermissionRegistry.resolve("GET", "/programs/options")).isEmpty();
		assertThat(PermissionRegistry.resolve("GET", "/programs/available")).isEmpty();
	}

	@Test
	void moreSpecificNestedPatternWinsOverItsBroaderSibling() {
		assertThat(PermissionRegistry.resolve("PUT", "/roles/abc/permissions")).contains("ROLES_ASSIGN_PERMISSIONS");
		assertThat(PermissionRegistry.resolve("POST", "/periods/advance-by-date")).contains("PERIODS_ADVANCE_BY_DATE");
	}

	@Test
	void unregisteredRouteFallsBackToTheCoarseLayerAlone() {
		assertThat(PermissionRegistry.resolve("DELETE", "/does-not-exist")).isEmpty();
	}

	// ── admin password reset ──────────────────────────────────────────────────

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

	// ── finance ───────────────────────────────────────────────────────────────

	/**
	 * The four adeudo operations share the {@code /finance} prefix and two of
	 * them share the {@code /payments} segment, so the specificity order is the
	 * whole point: a broad {@code GET /finance/payments/**} must not shadow the
	 * exact {@code GET /finance/payments}, and the nested entries path must win
	 * over the create path.
	 */
	@Test
	void financeRoutesResolveToTheirOwnPermissions() {
		assertThat(PermissionRegistry.resolve("GET", "/finance/quote")).contains("PAYMENTS_QUOTE");
		assertThat(PermissionRegistry.resolve("GET", "/finance/account-statement")).contains("PAYMENTS_READ");
		assertThat(PermissionRegistry.resolve("GET", "/finance/payments")).contains("PAYMENTS_READ");
		assertThat(PermissionRegistry.resolve("GET", "/finance/payments/" + USER_ID)).contains("PAYMENTS_READ");
		assertThat(PermissionRegistry.resolve("POST", "/finance/payments")).contains("PAYMENTS_CREATE");
		assertThat(PermissionRegistry.resolve("POST", "/finance/payments/" + USER_ID + "/entries"))
				.contains("PAYMENTS_SETTLE");
	}
}
