package mx.edu.utez.sisa.identity.infrastructure.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Fine-grained permission registry (roles-permisos.md §3.1, §3.3): maps
 * (HTTP method, Ant path) to the single permission key that governs it. The
 * coarse role matchers in {@link SecurityFilterConfig} stay untouched as the
 * outer layer — {@code ROLE_ADMIN} bypasses this inner layer entirely — and
 * {@link PermissionFilter} consults this registry to require the mapped
 * permission for every authenticated non-admin request.
 * <p>
 * Reference-class catalogs ({@code /states}, {@code /municipalities}, and
 * every path whose last segment is {@code options} or {@code available}) are
 * deliberately NOT registered: they are gated only by
 * {@code .authenticated()} in the coarse layer. Any path ending in the
 * {@code options} or {@code available} suffix therefore short-circuits
 * before pattern matching — otherwise a registered single-segment pattern
 * like {@code /plans/{id}} would also match
 * {@code /plans/options} (roles-permisos.md §3.3, "clase reference").
 * <p>
 * Order matters within the registry exactly as it does in
 * {@code SecurityFilterConfig}: the more specific nested patterns are
 * declared before their broader siblings (e.g.
 * {@code PUT /roles/{id}/permissions} before {@code PUT /roles/**},
 * {@code POST /periods/advance-by-date} before {@code POST /periods}),
 * so the first match wins with the precise key.
 */
public final class PermissionRegistry {

	private static final String PERMISSIONS_PATH_SUFFIX_OPTIONS = "/options";

	private static final String PERMISSIONS_PATH_SUFFIX_AVAILABLE = "/available";

	private static final List<Entry> ENTRIES = List.of(
			// identity — roles
			e("GET", "/roles/**", "ROLES_READ"),
			e("POST", "/roles", "ROLES_CREATE"),
			e("PUT", "/roles/*/permissions", "ROLES_ASSIGN_PERMISSIONS"),
			e("PUT", "/roles/**", "ROLES_UPDATE"),
			e("PATCH", "/roles/**", "ROLES_CHANGE_STATUS"),
			// identity — permissions
			e("GET", "/permissions/**", "PERMISSIONS_READ"),
			e("POST", "/permissions", "PERMISSIONS_CREATE"),
			e("PUT", "/permissions/**", "PERMISSIONS_UPDATE"),
			e("PATCH", "/permissions/**", "PERMISSIONS_CHANGE_STATUS"),
			// identity — users
			e("GET", "/users/**", "USERS_READ"),
			e("POST", "/users", "USERS_CREATE"),
			e("POST", "/users/*/roles", "USERS_ASSIGN_ROLE"),
			e("DELETE", "/users/*/roles/*", "USERS_REVOKE_ROLE"),
			e("PATCH", "/users/*/unlock", "USERS_UNLOCK"),
			// identity — persons
			e("GET", "/persons/**", "PERSONS_READ"),
			e("POST", "/persons", "PERSONS_CREATE"),
			// academic_config — divisions
			e("GET", "/divisions/**", "DIVISIONS_READ"),
			e("POST", "/divisions", "DIVISIONS_CREATE"),
			e("PUT", "/divisions/**", "DIVISIONS_UPDATE"),
			e("PATCH", "/divisions/**", "DIVISIONS_CHANGE_STATUS"),
			// academic_config — programs
			e("GET", "/programs/**", "CARRERAS_READ"),
			e("POST", "/programs", "CARRERAS_CREATE"),
			e("PUT", "/programs/**", "CARRERAS_UPDATE"),
			e("PATCH", "/programs/**", "CARRERAS_CHANGE_STATUS"),
			// academic_config — plans
			e("GET", "/plans/**", "PLANS_READ"),
			e("POST", "/plans/**", "PLANS_CREATE"),
			e("PUT", "/plans/**", "PLANS_UPDATE"),
			e("PATCH", "/plans/**", "PLANS_CHANGE_STATUS"),
			e("DELETE", "/plans/**", "PLANS_DELETE"),
			// academic_config — subject classifications
			e("GET", "/subject-classifications/**", "SUBJECT_CLASSIFICATIONS_READ"),
			e("POST", "/subject-classifications", "SUBJECT_CLASSIFICATIONS_CREATE"),
			e("PUT", "/subject-classifications/**", "SUBJECT_CLASSIFICATIONS_UPDATE"),
			e("PATCH", "/subject-classifications/**", "SUBJECT_CLASSIFICATIONS_CHANGE_STATUS"),
			// academic_config — periods
			e("GET", "/periods/**", "PERIODS_READ"),
			e("POST", "/periods/advance-by-date", "PERIODS_ADVANCE_BY_DATE"),
			e("POST", "/periods", "PERIODS_CREATE"),
			e("PUT", "/periods/**", "PERIODS_UPDATE"),
			e("PATCH", "/periods/**", "PERIODS_CHANGE_STATUS"),
			// academic_config — generations
			e("GET", "/generations/**", "GENERATIONS_READ"),
			e("POST", "/generations", "GENERATIONS_CREATE"),
			e("PUT", "/generations/**", "GENERATIONS_UPDATE"),
			e("PATCH", "/generations/**", "GENERATIONS_CHANGE_STATUS"),
			// academic_config — groups
			e("GET", "/groups/**", "GROUPS_READ"),
			e("POST", "/groups", "GROUPS_CREATE"),
			e("PUT", "/groups/**", "GROUPS_UPDATE"),
			e("PATCH", "/groups/**", "GROUPS_CHANGE_STATUS"),
			// academic_config — payment concepts (ADMIN/PERSONAL_FINANZAS in coarse)
			e("GET", "/payment-concepts/**", "PAYMENT_CONCEPTS_READ"),
			e("POST", "/payment-concepts/*/rates", "PAYMENT_RATES_CREATE"),
			e("POST", "/payment-concepts", "PAYMENT_CONCEPTS_CREATE"),
			e("PUT", "/payment-concepts/**", "PAYMENT_CONCEPTS_UPDATE"),
			e("PATCH", "/payment-concepts/**", "PAYMENT_CONCEPTS_CHANGE_STATUS"),
			// academic_config — program admission configs
			e("GET", "/program-admission-configs/**", "PROGRAM_ADMISSION_CONFIGS_READ"),
			e("POST", "/program-admission-configs", "PROGRAM_ADMISSION_CONFIGS_CREATE"),
			e("PUT", "/program-admission-configs/**", "PROGRAM_ADMISSION_CONFIGS_UPDATE"),
			e("PATCH", "/program-admission-configs/**", "PROGRAM_ADMISSION_CONFIGS_CHANGE_STATUS"),
			// admission — outreach channels
			e("GET", "/outreach-channels/**", "OUTREACH_CHANNELS_READ"),
			e("POST", "/outreach-channels", "OUTREACH_CHANNELS_CREATE"),
			e("PUT", "/outreach-channels/**", "OUTREACH_CHANNELS_UPDATE"),
			e("PATCH", "/outreach-channels/**", "OUTREACH_CHANNELS_CHANGE_STATUS"),
			// admission — high school types
			e("GET", "/high-school-types/**", "HIGH_SCHOOL_TYPES_READ"),
			e("POST", "/high-school-types", "HIGH_SCHOOL_TYPES_CREATE"),
			e("PUT", "/high-school-types/**", "HIGH_SCHOOL_TYPES_UPDATE"),
			e("PATCH", "/high-school-types/**", "HIGH_SCHOOL_TYPES_CHANGE_STATUS"));

	private static final AntPathMatcher MATCHER = new AntPathMatcher();

	private PermissionRegistry() {
	}

	/**
	 * Resolves the permission key governing the given request, or
	 * {@link java.util.Optional#empty()} when the request is not governed by
	 * the fine-grained layer (reference catalogs and unregistered routes fall
	 * back to the coarse role matchers alone).
	 */
	public static java.util.Optional<String> resolve(String method, String requestUri) {
		if (isReferenceClass(requestUri)) {
			return java.util.Optional.empty();
		}
		for (Entry entry : ENTRIES) {
			if (entry.method().equals(method) && MATCHER.match(entry.pattern(), requestUri)) {
				return java.util.Optional.of(entry.permissionKey());
			}
		}
		return java.util.Optional.empty();
	}

	private static boolean isReferenceClass(String requestUri) {
		return requestUri.endsWith(PERMISSIONS_PATH_SUFFIX_OPTIONS)
				|| requestUri.endsWith(PERMISSIONS_PATH_SUFFIX_AVAILABLE);
	}

	private static Entry e(String method, String pattern, String permissionKey) {
		return new Entry(method, pattern, permissionKey);
	}

	private record Entry(String method, String pattern, String permissionKey) {
	}
}