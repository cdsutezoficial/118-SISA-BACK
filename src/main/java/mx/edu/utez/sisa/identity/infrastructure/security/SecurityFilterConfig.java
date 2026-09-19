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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;

/**
 * Stateless security filter chain (design.md — Security filter chain): CSRF
 * disabled, {@code permitAll} on {@code /auth/login} and
 * {@code /auth/refresh}, everything else requires authentication.
 * {@code GET /users} (01-identidad.md — ListUsersUseCase) is matched
 * BEFORE the blanket {@code /users/**} rule and allows ADMIN or
 * SERVICIOS_ESCOLARES; every other {@code /users/**} path (create user,
 * assign role) stays ADMIN-only via the blanket rule. Matcher order matters:
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
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} (same pair as {@code GET /users})
 * and a POST matcher granting {@code ADMIN} only (same level as
 * {@code POST /users}). The existing {@code GET /users} matcher's pattern
 * list is extended to also cover {@code /users/**} so
 * {@code GET /users/{id}} (the new detail endpoint from the same plan)
 * shares the ADMIN/SERVICIOS_ESCOLARES pair instead of falling through to
 * the ADMIN-only blanket {@code /users/**} rule below it — the three other
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
 * {@code "/payment-concepts/**"}), so no new GET matcher is needed. The
 * existing {@code POST /payment-concepts} matcher, however, is an EXACT
 * pattern with no wildcard (unlike GET/PUT/PATCH on this endpoint) — it does
 * NOT match the nested {@code POST .../rates} path, so a dedicated
 * {@code POST "/payment-concepts/{conceptId}/rates"} matcher is added right
 * after it.
 * There is no PUT/PATCH/DELETE on {@code PaymentRate} (no Update/Delete by
 * design — plan section 4, append-only history), so no matcher is added for
 * those verbs.
 * {@code /program-admission-configs} (academic_config — tenth aggregate,
 * plan: {@code docs/plans/2026-07-28-program-admission-config.md}) gets the
 * identical GET/POST/PUT/PATCH four-matcher shape as every prior aggregate,
 * back to the {@code ADMIN}/{@code SERVICIOS_ESCOLARES} pair (NOT
 * {@code PERSONAL_FINANZAS} — PO-confirmed 2026-07-28: unlike
 * {@code PaymentConcept}, there is no role in the 11-role catalog dedicated
 * to "admisión", so this aggregate follows the module's default pair even
 * though its future consumer is the Admisión module). Placed right after the
 * {@code /payment-concepts/.../rates} matcher.
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
 * a single GET matcher each, {@code .authenticated()} with NO role
 * restriction — these are read-only catalogs any authenticated user's form
 * may need to query (e.g. a future candidate registration screen), and the
 * domain doc gives no business reason to gate them by role. There is no
 * POST/PUT/PATCH matcher for either — both are closed, seed-once catalogs
 * with no write endpoints at all (see {@code StateController}/
 * {@code MunicipalityController}). Placed right after the
 * {@code /high-school-types} matchers.
 * {@link JwtAuthenticationFilter} runs before
 * {@code UsernamePasswordAuthenticationFilter}.
 */
@Configuration
@EnableWebSecurity
public class SecurityFilterConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final CorsConfigurationSource corsConfigurationSource;

	private final ObjectMapper objectMapper;

	public SecurityFilterConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			CorsConfigurationSource corsConfigurationSource, ObjectMapper objectMapper) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
						.requestMatchers("/auth/login", "/auth/refresh").permitAll()
						.requestMatchers(HttpMethod.GET, "/roles", "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/roles").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/roles/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/permissions", "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/permissions").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PATCH, "/permissions/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/users", "/users/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
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
						.requestMatchers(HttpMethod.POST, "/payment-concepts/*/rates")
						.hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
						.requestMatchers(HttpMethod.GET, "/program-admission-configs", "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/program-admission-configs")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/program-admission-configs/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/outreach-channels/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/outreach-channels", "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/outreach-channels")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/outreach-channels/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/high-school-types/options").authenticated()
						.requestMatchers(HttpMethod.GET, "/high-school-types", "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.POST, "/high-school-types")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PUT, "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.PATCH, "/high-school-types/**")
						.hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers(HttpMethod.GET, "/states").authenticated()
						.requestMatchers(HttpMethod.GET, "/municipalities").authenticated()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
