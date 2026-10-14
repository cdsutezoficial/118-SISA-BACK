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
 *
 * <p>Every SISA token is signed with one secret, so the signature alone cannot
 * tell token families apart — anything this module verifies is, cryptographically,
 * "a token we issued". {@link #TYPE_CLAIM} is what makes the families
 * distinguishable, and {@link #parseAccessToken} requires it. Without that check
 * the candidate identity ticket (same secret, valid, unexpired) would be accepted
 * by the authentication filter as a session: it carries no {@code roles} claim, so
 * every role-gated endpoint would still refuse it, but any endpoint gated only
 * on {@code authenticated()} would admit a candidate who has no SISA account at
 * all. Stamping the type on sign and demanding it on parse makes the two families
 * fail closed against each other by construction.
 */
@Component
public class JwtService {

	/** {@code typ} claim value identifying an access token. */
	public static final String ACCESS_TOKEN_TYPE = "access-token";

	private static final String TYPE_CLAIM = "typ";

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
				.claim(TYPE_CLAIM, ACCESS_TOKEN_TYPE)
				.claim("roles", roles)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)))
				.signWith(key)
				.compact();
	}

	/**
	 * Parses a token and asserts it really is an access token.
	 *
	 * @throws io.jsonwebtoken.JwtException when the signature or expiry fails, or
	 *                                       when the token is a valid SISA token of
	 *                                       another family (a candidate
	 *                                       verification ticket)
	 */
	public Claims parseAccessToken(String token) {
		Claims claims = parse(token);
		if (!ACCESS_TOKEN_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
			// Message names the shape, never the claims: this text can reach a log.
			throw new io.jsonwebtoken.JwtException("El token no es un token de acceso.");
		}
		return claims;
	}

	/** Raw signature + expiry check. Use {@link #parseAccessToken} to authenticate. */
	public Claims parse(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
