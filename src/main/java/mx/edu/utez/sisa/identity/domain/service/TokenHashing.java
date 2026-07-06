package mx.edu.utez.sisa.identity.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pure SHA-256 hashing utility for opaque refresh token values (design.md —
 * Decision: Refresh endpoint). Only the hash is ever persisted; the
 * plaintext token is handed to the client and never stored, mirroring
 * {@code PasswordResetToken}.
 */
public final class TokenHashing {

	private TokenHashing() {
	}

	public static String sha256(String rawValue) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
}
