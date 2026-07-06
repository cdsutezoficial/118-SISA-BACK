package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	public String issue(UUID userId, Set<RoleType> roles) {
		Set<String> roleNames = roles.stream().map(Enum::name).collect(Collectors.toSet());
		return jwtService.sign(userId.toString(), roleNames);
	}
}
