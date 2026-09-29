package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * {@link AccessTokenIssuer} adapter delegating signing to {@link JwtService}.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

	private final JwtService jwtService;

	public JwtAccessTokenIssuer(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public String issue(UUID userId, Set<String> roles) {
		return jwtService.sign(userId.toString(), roles);
	}
}
