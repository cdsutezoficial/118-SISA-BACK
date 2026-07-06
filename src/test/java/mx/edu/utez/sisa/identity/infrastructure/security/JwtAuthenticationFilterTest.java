package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies task 5.3: Bearer header -> {@code JwtService.parse} -> {@code
 * roles} claim mapped to {@code SecurityContext} authorities.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtService jwtService;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;

	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		filter = new JwtAuthenticationFilter(jwtService);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void validBearerTokenPopulatesSecurityContextWithRoleAuthorities() throws Exception {
		String userId = "11111111-1111-1111-1111-111111111111";
		when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
		Claims claims = mock(Claims.class);
		when(claims.getSubject()).thenReturn(userId);
		when(claims.get("roles", List.class)).thenReturn(List.of("ADMIN", "DOCENTE"));
		when(jwtService.parse("valid-token")).thenReturn(claims);

		filter.doFilterInternal(request, response, filterChain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getName()).isEqualTo(userId);
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ADMIN",
				"ROLE_DOCENTE");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void missingAuthorizationHeaderLeavesSecurityContextEmpty() throws Exception {
		when(request.getHeader("Authorization")).thenReturn(null);

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void invalidTokenClearsSecurityContextAndContinuesChain() throws Exception {
		when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
		when(jwtService.parse(anyString())).thenThrow(new JwtException("malformed"));

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void nonBearerAuthorizationHeaderIsIgnored() throws Exception {
		when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}
}
