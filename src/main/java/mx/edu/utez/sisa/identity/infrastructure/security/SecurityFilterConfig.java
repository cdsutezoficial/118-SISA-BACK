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
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;

/**
 * Stateless security filter chain (design.md — Security filter chain): CSRF
 * disabled, {@code permitAll} on {@code /auth/login}, {@code /auth/refresh}
 * and {@code /h2-console/**} (dev), everything else requires authentication.
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
				.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/login", "/auth/refresh", "/h2-console/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/users").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
						.requestMatchers("/users/**").hasRole("ADMIN")
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
					HttpStatus.UNAUTHORIZED.getReasonPhrase(), "Invalid or missing authentication token",
					request.getRequestURI());
			objectMapper.writeValue(response.getWriter(), body);
		};
	}
}
