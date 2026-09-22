package mx.edu.utez.sisa.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fine-grained authorization filter (roles-permisos.md §3.1): runs after
 * {@link JwtAuthenticationFilter} has populated the
 * {@link SecurityContextHolder} and before Spring Security's
 * {@code AuthorizationFilter} enforces the coarse role matchers. For every
 * route registered in {@link PermissionRegistry} it requires the mapped
 * permission key in the caller's cache union; a missing key answers 403 with
 * the same JSON body as the coarse {@code AccessDeniedHandler}.
 * <p>
 * Bypasses (they fall through to the coarse layer unchanged): anonymous
 * requests (not authenticated at all — 401 handled downstream), requests
 * carrying {@code ROLE_ADMIN} (superuser safeguard, §3.1), reference-class
 * catalogs and unregistered routes.
 */
@Component
public class PermissionFilter extends OncePerRequestFilter {

	private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private static final String ADMIN_ROLE_KEY = "ADMIN";

	private final PermissionCache permissionCache;

	private final ObjectMapper objectMapper;

	public PermissionFilter(PermissionCache permissionCache, ObjectMapper objectMapper) {
		this.permissionCache = permissionCache;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			filterChain.doFilter(request, response);
			return;
		}

		Set<String> roleKeys = authorityRoleKeys(authentication);
		if (roleKeys.contains(ADMIN_ROLE_KEY)) {
			filterChain.doFilter(request, response);
			return;
		}

		Optional<String> requiredPermission = PermissionRegistry.resolve(request.getMethod(), request.getRequestURI());
		if (requiredPermission.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		if (permissionCache.permissionKeysFor(roleKeys).contains(requiredPermission.get())) {
			filterChain.doFilter(request, response);
			return;
		}

		writeForbidden(request, response);
	}

	private static Set<String> authorityRoleKeys(Authentication authentication) {
		Set<String> roleKeys = new HashSet<>();
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			String value = authority.getAuthority();
			if (value != null && value.startsWith(ROLE_AUTHORITY_PREFIX)) {
				roleKeys.add(value.substring(ROLE_AUTHORITY_PREFIX.length()));
			}
		}
		return roleKeys;
	}

	private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.FORBIDDEN.value(), "Acceso denegado",
				"No tienes permiso para realizar esta acción.", request.getRequestURI());
		objectMapper.writeValue(response.getWriter(), body);
	}
}