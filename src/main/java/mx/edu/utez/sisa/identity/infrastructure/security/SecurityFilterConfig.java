package mx.edu.utez.sisa.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;

/**
 * Stateless security filter chain (design.md — Security filter chain): CSRF
 * disabled, {@code permitAll} on {@code /auth/login}, {@code /auth/refresh},
 * {@code /auth/forgot-password} and {@code /auth/reset-password} (the latter
 * pair public for the "forgot my password" flow — 01-identidad.md — since the
 * user has no session when starting or completing a reset), everything else
 * requires authentication.
 * {@code GET /users} (01-identidad.md — ListUsersUseCase) is matched
 * BEFORE the blanket {@code /users/**} rule and allows only ADMIN; every
 * other {@code /users/**} path (create user, assign role) stays ADMIN-only
 * via the blanket rule. Matcher order matters:
 * Spring Security evaluates {@code authorizeHttpRequests} rules in
 * declaration order and applies the first match, so the specific GET rule
 * must be declared first or it would never be reached.
 * {@code /divisions} (academic_config — design.md's "Security matcher
 * (order matters)") gets FOUR separate matchers, one per HTTP verb (GET,
 * POST, PUT, PATCH), all currently granting the same
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair — split by verb now (rather
 * than one blanket {@code /divisions/**} rule) so a future read-only-only
 * role is a one-line addition to just the GET matcher. All four MUST precede
 * {@code anyRequest()}.
 * {@code /programs} (academic_config — HU-PROG-010, second aggregate) gets
 * the identical FOUR verb-split matchers, same
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair, placed right after the
 * {@code /divisions} matchers for the same one-line-future-change rationale.
 * {@code /plans} (academic_config — third aggregate, "Academic Plan
 * Management") gets FIVE verb-split matchers — the same four verbs as
 * Program plus {@code DELETE}, since unlike the root aggregates (never
 * hard-deleted), the nested {@code PlanLevel}/{@code Subject} child
 * endpoints support real deletion (design.md — "Unlike the root ... children
 * DO support real DELETE"). A single {@code "/plans/**"} pattern per verb
 * covers both the root and every nested level/subject path. Placed right
 * after the {@code /programs} matchers for the same rationale.
 * {@code /subject-classifications} (academic_config — fourth aggregate) gets
 * a GET matcher (Phase 1 "Consulta/List", already {@code /subject-classifications/**}
 * so it also covers Phase 3's {@code GET /subject-classifications/{id}}
 * without a new matcher), a POST matcher (Phase 2 "Registro/Create"), a
 * PUT matcher (Phase 4 "Actualización/Update", its own line since the GET
 * matcher is verb-scoped and does not cover other verbs' paths), and a PATCH
 * matcher (Phase 5 "Cambio de estado/ChangeStatus", also its own line for the
 * same verb-scoped reason as PUT), all four
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} — same pair as every other verb
 * on this endpoint. The aggregate's full CRUD is now covered.
 * {@code /periods} (academic_config — fifth aggregate, a full standalone
 * aggregate root like {@code /subject-classifications} rather than a
 * child-of-another-aggregate like {@code GradeScale}) gets the identical
 * GET/POST/PUT/PATCH four-matcher shape, same
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair, placed right after the
 * {@code /subject-classifications} matchers for the same one-line-future-change
 * rationale.
 * {@code /generations} (academic_config — sixth aggregate, plan:
 * {@code docs/plans/2026-07-20-generation-group.md}) gets the identical
 * GET/POST/PUT/PATCH four-matcher shape, same
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair, placed right after the
 * {@code /periods} matchers for the same one-line-future-change rationale.
 * {@code /groups} (academic_config — seventh aggregate, same plan, "Group —
 * diseño técnico resuelto (2026-07-23)") gets the identical GET/POST/PUT/PATCH
 * four-matcher shape, same {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair,
 * placed right after the {@code /generations} matchers for the same
 * one-line-future-change rationale.
 * {@code /persons} (identity, plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md}) gets a GET
 * matcher ({@code /persons}, {@code /persons/**}) granting
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES}
 * and a POST matcher granting {@code ADMIN} only (same level as
 * {@code POST /users}). The existing {@code GET /users} matcher's pattern
 * list is extended to also cover {@code /users/**} so
 * {@code GET /users/{id}} (the new detail endpoint from the same plan)
 * is served ADMIN-only, matching the blanket {@code /users/**} rule below
 * it — the three other
 * new endpoints on that plan ({@code DELETE .../roles/{userRoleId}},
 * {@code PATCH .../unlock}, plus the existing {@code POST} endpoints) are
 * NOT GET, so they still fall through to the ADMIN-only blanket rule
 * unchanged.
 * {@code /payment-concepts} (academic_config — eighth aggregate, Fase 1 of 4
 * of "Conceptos de Pago", plan: {@code docs/plans/2026-07-28-payment-concept.md})
 * gets the identical GET/POST/PUT/PATCH four-matcher shape as every prior
 * aggregate, but grants {@code ADMIN}/{@code PERSONAL_FINANZAS} instead of
 * the {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair every other
 * {@code academic_config} matcher above uses — deliberately, not an
 * oversight. RF-PAG-001 states the requirement as "Como finanzas quiero
 * gestionar...", and the domain design types
 * {@code PaymentBenefit.approvedBy} explicitly as
 * {@code FK -> User (PERSONAL_FINANZAS)}: this catalog is administered by
 * Finanzas, not Servicios Escolares, even though the aggregate itself lives
 * in the {@code academic_config} bounded context. Placed right after the
 * {@code /persons} matchers.
 * {@code /payment-concepts/{conceptId}/rates} (academic_config — Fase 2 of 4
 * of "Conceptos de Pago", plan: {@code docs/plans/2026-07-28-payment-rate.md})
 * is nested under {@code /payment-concepts}, same {@code ADMIN}/
 * {@code PERSONAL_FINANZAS} pair. The existing {@code GET /payment-concepts}
 * matcher already covers it (its pattern list includes the wildcarded
 * {@code "/payment-concepts/**"}), so no new GET matcher is needed, and the
 * wildcarded {@code PUT /payment-concepts/**} matcher covers the write side:
 * prices are reconciled with {@code PUT} on the whole rate set, not posted one
 * row at a time.
 * There is no PATCH/DELETE on {@code PaymentRate} — withdrawing a price is done
 * by omitting the destination from the set, which deactivates the row and keeps
 * it in the history — so no matcher is added for those verbs.
 * {@code /payment-areas} (academic_config — companion catalog to
 * {@code PaymentConcept}, plan: {@code docs/plans/2026-09-19-payment-areas.md})
 * gets the identical GET/POST/PUT/PATCH four-matcher shape and the same
 * {@code ADMIN}/{@code PERSONAL_FINANZAS} pair as {@code /payment-concepts}
 * (same bounded-context co-location and same Finanzas ownership rationale).
 * Its {@code GET /payment-areas/options} reference picker is
 * {@code authenticated()} (same as {@code /divisions/options}) and is
 * declared BEFORE the blanket {@code GET /payment-areas/**} rule.
 * {@code /program-admission-configs} (academic_config — tenth aggregate,
 * plan: {@code docs/plans/2026-07-28-program-admission-config.md}) gets the
 * identical GET/POST/PUT/PATCH four-matcher shape as every prior aggregate,
 * back to the {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair (NOT
 * {@code PERSONAL_FINANZAS} — PO-confirmed 2026-07-28: unlike
 * {@code PaymentConcept}, there is no role in the 11-role catalog dedicated
 * to "admisión", so this aggregate follows the module's default pair even
 * though its future consumer is the Admisión module). Its GET matcher also
 * grants {@code DIRECTOR_DIVISION} (read-only visibility of the Admisión
 * module's configuration from the sidebar) — the mutating verbs stay
 * ADMIN/SERVICIOS_ESCOLARES. Placed right after the
 * {@code /payment-concepts/**} matchers.
 * {@code GET /program-admission-configs/options} (same plan, new for the
 * ficha de admisión — plan: {@code docs/plans/sisa-candidate-ficha.md}) is a
 * public reference picker listing the currently-{@code OPEN} configs with
 * their program name + modality, so the public registration wizard can map a
 * program to its {@code admissionConfigId}. {@code permitAll()}, declared
 * BEFORE the blanket {@code GET /program-admission-configs/**} rule (which
 * still requires ADMIN/SERVICIOS_ESCOLARES/DIRECTOR_DIVISION for the
 * management list).
 * {@code /outreach-channels} (eleventh matcher block, but the FIRST from the
 * NEW {@code admission} bounded context — plan:
 * {@code docs/plans/2026-07-28-outreach-channel.md} — rather than another
 * {@code academic_config} aggregate) gets the identical GET/POST/PUT/PATCH
 * four-matcher shape as every prior aggregate, {@code ADMIN}/
 * {@code SERVICIOS_ESCOLARES} pair (same rationale as
 * {@code /program-admission-configs}: no role in the 11-role catalog is
 * dedicated to "admisión"). Security remains centralized in this file
 * regardless of which bounded context owns the resource — {@code identity}
 * already matches its own {@code /users}, and {@code academic_config}'s ten
 * aggregates live here too, so a brand-new bounded context is no exception.
 * Placed right after the {@code /program-admission-configs} matchers.
 * {@code /high-school-types} ({@code admission}'s second aggregate, Fase A of
 * plan: {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md})
 * gets the identical GET/POST/PUT/PATCH four-matcher shape as
 * {@code /outreach-channels}, same {@code ADMIN}/{@code SERVICIOS_ESCOLARES}
 * pair, placed right after the {@code /outreach-channels} matchers.
 * {@code /states} and {@code /municipalities} (shared-kernel INEGI reference
 * catalogs, same plan) are deliberately DIFFERENT from every matcher above:
 * a single GET matcher each, {@code .permitAll()} with NO role/authentication
 * restriction — these are closed, seed-once read-only catalogs the PUBLIC
 * registration wizard (Screen 4's anonymous {@code /portal/registro} mount,
 * same plan as {@code POST /candidates}) needs to resolve state/municipality
 * names to {@code UUID} ids before posting the ficha, and the domain doc
 * gives no business reason to hide them. There is no POST/PUT/PATCH matcher
 * for either — both are closed, seed-once catalogs with no write endpoints
 * at all (see {@code StateController}/{@code MunicipalityController}). Placed
 * right after the {@code /high-school-types} matchers.
 * {@code GET /outreach-channels/options} and
 * {@code GET /high-school-types/options} (same plan/screen rationale as
 * {@code /states}: the anonymous wizard resolves channel/school-type names to
 * {@code UUID}) are likewise {@code .permitAll()}, declared BEFORE their
 * adjacent ADMIN/SERVICIOS_ESCOLARES management matchers.
 * {@code GET /program-admission-configs/{id}/ficha-amount} (Fase 11) joins them
 * for the same reason: the wizard's review step previews the catalog-priced
 * ficha amount before the ticket exists, so an applicant with no session must
 * be able to read the quote.
 * {@code POST /candidates} (the LAST matcher block — {@code admission}'s
 * third aggregate/first real flow, the public "ficha de admisión" endpoint,
 * plan: {@code docs/plans/sisa-candidate-ficha.md}) is the ONLY pre-authenticated
 * POST in the app: {@code .permitAll()}, declared FIRST in the chain together
 * with {@code /auth/login}/{@code /auth/refresh} for the same fundamental
 * reason they are public — the applicant has no session when they submit their
 * ficha from the public portal. Registration is the single intentionally
 * anonymous write; every other {@code /candidates/**} verb (admin list/detail/
 * transitions) will live under the {@code ADMIN}/{@code SERVICIOS_ESCOLARES}
 * pair when implemented.
 * {@link JwtAuthenticationFilter} runs before
 * {@code UsernamePasswordAuthenticationFilter}.
 * {@link PermissionFilter} (roles-permisos.md §3.1) runs after the JWT filter
 * and before the coarse {@code AuthorizationFilter}: for registered routes it
 * requires the permission key from the in-memory cache
 * ({@link PermissionCache}, §3.2), relying on the coarse matchers in
 * {@code authorizeHttpRequests} as the outer role layer.
 */
@Configuration
@EnableWebSecurity
public class SecurityFilterConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final PermissionFilter permissionFilter;

	private final CorsConfigurationSource corsConfigurationSource;

	private final ObjectMapper objectMapper;

	public SecurityFilterConfig(JwtAuthenticationFilter jwtAuthenticationFilter, PermissionFilter permissionFilter,
			CorsConfigurationSource corsConfigurationSource, ObjectMapper objectMapper) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.permissionFilter = permissionFilter;
		this.corsConfigurationSource = corsConfigurationSource;
		this.objectMapper = objectMapper;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler()))
				.authorizeHttpRequests(auth -> auth
					.requestMatchers(HttpMethod.POST, "/candidates", "/candidates/payment-access",
							"/candidates/*/payments/confirm", "/candidates/*/payments/checkout",
							"/candidates/*/payments/release")
					.permitAll()
						.requestMatchers(HttpMethod.GET, "/candidates/*", "/candidates/*/ficha.pdf",
								"/candidates/*/payment-access").permitAll()
						// Staff candidate list. Exact "/candidates" — NOT covered by the
						// public "/candidates/*" matcher above (a trailing "/{segment}" is
						// required), so it must be declared explicitly. ADMIN bypasses the
						// fine-grained layer; SE/Director go through CANDIDATES_READ.
						// Finanzas too (2026-10-22): it grants discounts on a
						// candidate's charges, and finds the candidate by folio/CURP
						// here. List only — the detail below stays SE/Director.
						.requestMatchers(HttpMethod.GET, "/candidates")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES", "DIRECTOR_DIVISION", "PERSONAL_FINANZAS")
						// Staff candidate detail. Two segments deep, so the public
						// "/candidates/*" matcher above does not reach it either.
						.requestMatchers(HttpMethod.GET, "/candidates/*/detail")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES", "DIRECTOR_DIVISION")
						// Edición de la ficha (Fase 7 · T2): solo Servicios Escolares.
						// El GET va dos segmentos abajo y el PUT es otro método, así
						// que el permitAll de GET "/candidates/*" no alcanza a ninguno.
						.requestMatchers(HttpMethod.GET, "/candidates/*/edit")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/candidates/*")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers("/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/reset-password")
						.permitAll()
						// Llave Morelos OAuth (RF-AUTH-002 / RF-AUTH-003). Public by
						// design: the whole point is that the caller has no SISA
						// session yet — start begins the round and the provider's
						// callback lands here with only an authorization code. The
						// proof of identity is the PKCE verifier in the flow cookie
						// plus the state round-trip, not a JWT.
						// Linking is the one LlaveMX endpoint that is NOT public:
						// unlike login (where the provider is the authenticator) and
						// candidate verification (where no account is touched),
						// this writes llave_mx_subject onto an existing User, so it
						// answers only to a signed-in ADMIN — the same role the
						// /usuarios/detalle screen that hosts the button already
						// requires.
						.requestMatchers(HttpMethod.POST, "/auth/llave-mx/link").hasRole("ADMIN")
						// Prefill is public for the same reason the callbacks are:
						// it answers after the provider round-tripped, when there is
						// still no session. Its credential is not the missing JWT but
						// the signed, HttpOnly verification ticket the callback left
						// behind, so an unauthenticated caller without that cookie
						// gets 403 from the ticket check, not anonymous data.
						.requestMatchers("/auth/llave-mx/start", "/auth/llave-mx/status",
								"/auth/llave-mx/prefill", "/auth/llave-mx/callback",
								"/oauth/llave/digital/callback",
								"/SISAVA/oauth/llave/digital/callback")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/roles", "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/roles").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/permissions", "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/permissions").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/users", "/users/**")
						.hasRole("ADMIN")
						.requestMatchers("/users/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/programs/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/divisions/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/plans/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/divisions", "/divisions/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/divisions").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/divisions/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/programs", "/programs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/programs").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/programs/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/programs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/plans", "/plans/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/plans", "/plans/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/plans/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/plans/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.DELETE, "/plans/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/subject-classifications/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/subject-classifications", "/subject-classifications/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/subject-classifications")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/subject-classifications/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/subject-classifications/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/periods/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/periods", "/periods/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/periods").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/periods/advance-by-date")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/periods/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/periods/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/generations/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/generations", "/generations/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/generations").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/generations/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/generations/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/groups", "/groups/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/config-academica/statistics")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/groups").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/groups/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/groups/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/persons", "/persons/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/persons").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/payment-concepts", "/payment-concepts/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.POST, "/payment-concepts")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.PUT, "/payment-concepts/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
					.requestMatchers(HttpMethod.PATCH, "/payment-concepts/**")
					.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
					// No hay matcher para `POST /payment-concepts/*/rates`: ese verbo
					// se retiró cuando las tarifas pasaron a escribirse como conjunto
					// con `PUT`. Dejarlo autorizaba una ruta que ya no existe, y el
					// filtro habría dado por bueno un 404 en vez del 403 que
					// corresponde a un rol sin permiso.
					.requestMatchers(HttpMethod.GET, "/payment-areas/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/payment-areas", "/payment-areas/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.POST, "/payment-areas")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.PUT, "/payment-areas/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.PATCH, "/payment-areas/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						// finance — núcleo de cobro (Fase 1, plan:
						// docs/design/pendientes/pagos/fases/fase-01-nucleo-cobro.md).
						// Primera superficie del bounded context `finance`; igual que
						// /payment-concepts, la Administra Finanzas, así que reutiliza
						// el par ADMIN/PERSONAL_FINANZAS. Un solo matcher por verbo
						// (GET y POST) sobre "/finance/**": cubre cotización, adeudos,
						// abonos y estado de cuenta sin listar cada ruta, y se apoya
						// en el permiso fino PAYMENTS_* (PermissionRegistry) para el
						// detalle. Debe preceder a anyRequest().
						// Los descuentos (Fase 2) los administra SE además de
						// Finanzas, así que su subárbol se autoriza por delante con
						// los tres roles: el matcher sin verbo cubre GET/POST/PUT/
						// PATCH de /finance/benefits/** y debe preceder a los de
						// /finance/** para ganar el first-match. El resto de
						// /finance/** (adeudos, cotización, saldo a favor) sigue en
						// el par ADMIN/PERSONAL_FINANZAS.
						.requestMatchers("/finance/benefits/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/finance/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.POST, "/finance/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.PUT, "/finance/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.PATCH, "/finance/**")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.GET, "/program-admission-configs/options").permitAll()
						.requestMatchers(HttpMethod.GET, "/program-admission-configs/*/ficha-amount").permitAll()
						.requestMatchers(HttpMethod.GET, "/program-admission-configs", "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES", "DIRECTOR_DIVISION")
						.requestMatchers(HttpMethod.POST, "/program-admission-configs")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/outreach-channels/options").permitAll()
						.requestMatchers(HttpMethod.GET, "/outreach-channels", "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/outreach-channels")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/high-school-types/options").permitAll()
						.requestMatchers(HttpMethod.GET, "/high-school-types", "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/high-school-types")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/postal-codes/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/states").permitAll()
						.requestMatchers(HttpMethod.GET, "/municipalities").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(permissionFilter, AuthorizationFilter.class);
		return http.build();
	}

	/**
	 * Without this, Spring Security's stateless-REST default returns 403 for
	 * BOTH "not authenticated at all" (missing/invalid token) and
	 * "authenticated but wrong role" — losing a distinction REST clients rely
	 * on (e.g. the frontend forces re-login only on 401, never on 403, since
	 * 403 can legitimately mean "logged in, just not allowed here"). This
	 * entry point fires only for the "not authenticated" case; role mismatches
	 * still fall through to Spring's default 403 {@code AccessDeniedHandler}.
	 * Found via live browser verification of the frontend's 401-handling hook,
	 * which never fired because the backend never actually sent a 401.
	 */
	private AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.UNAUTHORIZED.value(),
					"No autorizado", "Tu sesión no es válida o ha expirado. Inicia sesión nuevamente.",
					request.getRequestURI());
			objectMapper.writeValue(response.getWriter(), body);
		};
	}

	private AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.FORBIDDEN.value(),
					"Acceso denegado", "No tienes permiso para realizar esta acción.",
					request.getRequestURI());
			objectMapper.writeValue(response.getWriter(), body);
		};
	}
}
