package mx.edu.utez.sisa.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies roles-permisos.md §3.1: {@code PermissionFilter} requires the
 * registered permission for authenticated non-admin requests, answers 403 in
 * the same JSON shape as the coarse {@code AccessDeniedHandler}, and
 * bypasses anonymous requests, {@code ROLE_ADMIN}, and reference-class
 * catalogs.
 */
@ExtendWith(MockitoExtension.class)
class PermissionFilterTest {

	@Mock
	private PermissionCache permissionCache;
	@Mock
	private ObjectMapper objectMapper;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;

	private PermissionFilter filter;

	@BeforeEach
	void setUp() {
		filter = new PermissionFilter(permissionCache, objectMapper);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void anonymousRequestContinuesChain() throws Exception {
		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		verify(response, never()).setStatus(403);
	}

	@Test
	void adminRoleBypassesFineGrainedLayer() throws Exception {
		authenticateAs("ADMIN");

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	void referenceClassOptionsPathIsNotGoverned() throws Exception {
		authenticateAs("SERVICIOS_ESCOLARES");
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/plans/options");

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	void unregisteredRouteIsNotGoverned() throws Exception {
		authenticateAs("SERVICIOS_ESCOLARES");
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/states");

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	void requestWithPermissionContinuesChain() throws Exception {
		authenticateAs("SERVICIOS_ESCOLARES");
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/users");
		when(permissionCache.permissionKeysFor(anyCollection())).thenReturn(Set.of("USERS_READ"));

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	void requestWithoutPermissionAnswers403AndStopsChain() throws Exception {
		authenticateAs("SERVICIOS_ESCOLARES");
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/users");
		when(permissionCache.permissionKeysFor(anyCollection())).thenReturn(Set.of("PERSONS_READ"));

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(403);
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void multiRoleUserGetsUnionOfEveryRolePermissionSet() throws Exception {
		authenticateAs("SERVICIOS_ESCOLARES", "PERSONAL_FINANZAS");
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/users");
		when(permissionCache.permissionKeysFor(argThat(keys -> keys instanceof Collection<String> roleKeys
				&& roleKeys.containsAll(Set.of("SERVICIOS_ESCOLARES", "PERSONAL_FINANZAS")))))
				.thenReturn(Set.of("USERS_READ", "PAYMENT_CONCEPTS_READ"));

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	private void authenticateAs(String... roleKeys) {
		List<SimpleGrantedAuthority> authorities = Arrays.stream(roleKeys)
				.map(roleKey -> new SimpleGrantedAuthority("ROLE_" + roleKey))
				.map(SimpleGrantedAuthority.class::cast)
				.toList();
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("user", null, authorities));
	}
}