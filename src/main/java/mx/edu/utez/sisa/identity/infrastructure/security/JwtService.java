package mx.edu.utez.sisa.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Signs and parses access tokens with {@code jjwt} (design.md — access
 * token: 30-minute TTL, claims {@code sub} = userId and {@code roles} = the
 * user's {@code RoleType} names only, no {@code divisionId}).
 */
@Component
public class JwtService {

	private final SecretKey key;
	private final Duration accessTokenTtl;

	public JwtService(@Value("${sisa.security.jwt.secret}") String secret,
			@Value("${sisa.security.jwt.access-token-ttl}") Duration accessTokenTtl) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = accessTokenTtl;
	}

	public String sign(String subject, Set<String> roles) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(subject)
				.claim("roles", roles)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)))
				.signWith(key)
				.compact();
	}

	public Claims parse(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
