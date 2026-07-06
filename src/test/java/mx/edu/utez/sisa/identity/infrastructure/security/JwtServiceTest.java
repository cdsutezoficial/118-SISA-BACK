package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

	private final JwtService jwtService = new JwtService(
			"dev-only-test-secret-must-be-at-least-256-bits-long-for-hs256!!", Duration.ofMinutes(30));

	@Test
	void signThenParseRoundTripPreservesSubjectAndRolesClaims() {
		String subject = UUID.randomUUID().toString();
		Set<String> roles = Set.of("ADMIN", "DOCENTE");

		String token = jwtService.sign(subject, roles);
		Claims claims = jwtService.parse(token);

		assertThat(claims.getSubject()).isEqualTo(subject);

		@SuppressWarnings("unchecked")
		List<String> parsedRoles = claims.get("roles", List.class);
		assertThat(Set.copyOf(parsedRoles)).isEqualTo(roles);
	}
}
