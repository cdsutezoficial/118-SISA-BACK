package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * {@link RefreshTokenGenerator} adapter producing an opaque, cryptographically
 * random URL-safe string (design.md — Decision: Refresh endpoint). Only the
 * SHA-256 hash of this value is ever persisted; this class has no knowledge
 * of hashing.
 */
@Component
public class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

	private static final int TOKEN_BYTE_LENGTH = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
