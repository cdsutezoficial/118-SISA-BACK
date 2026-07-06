package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it via
 * {@link JwtService}, and maps the {@code roles} claim to
 * {@code ROLE_*} authorities in the {@code SecurityContext} (design.md —
 * Security filter chain). Runs before {@code UsernamePasswordAuthenticationFilter}
 * (wired in {@link SecurityFilterConfig}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			try {
				Claims claims = jwtService.parse(token);
				SecurityContextHolder.getContext().setAuthentication(buildAuthentication(claims));
			}
			catch (JwtException | IllegalArgumentException ex) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	@SuppressWarnings("unchecked")
	private Authentication buildAuthentication(Claims claims) {
		String userId = claims.getSubject();
		List<String> roles = claims.get("roles", List.class);
		Set<SimpleGrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toSet());
		return new UsernamePasswordAuthenticationToken(userId, null, authorities);
	}
}
