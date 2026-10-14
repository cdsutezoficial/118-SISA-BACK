package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates the PKCE verifier/challenge pair and the anti-CSRF state for one
 * Llave Morelos redirect round.
 *
 * <p>Deliberately stateless: the caller persists the values for the duration of
 * the round (a short-lived HttpOnly cookie in the web flow) rather than this
 * adapter holding server-side sessions. Keeping it stateless means several API
 * instances behind a load balancer can all finish a flow started on any of
 * them.
 */
public final class LlaveMxPkceGenerator {

	/** RFC 7636 §4.1: 43-128 characters from the unreserved set. */
	private static final int VERIFIER_BYTES = 32;
	private static final int STATE_BYTES = 24;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final SecureRandom RANDOM = new SecureRandom();

	private LlaveMxPkceGenerator() {
	}

	/**
	 * High-entropy {@code code_verifier}. 32 random bytes → 43 base64url
	 * characters, the RFC's minimum length.
	 */
	public static String newCodeVerifier() {
		byte[] bytes = new byte[VERIFIER_BYTES];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	/** Anti-CSRF nonce echoed back by the provider in the callback. */
	public static String newState() {
		byte[] bytes = new byte[STATE_BYTES];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	/**
	 * {@code code_challenge = BASE64URL(SHA256(code_verifier))} — the S256
	 * transform the provider requires ({@code code_challenge_method=S256}).
	 */
	public static String challengeOf(String codeVerifier) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return ENCODER.encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the platform; its absence is a broken JVM.
			throw new LlaveMxIntegrationException("No se pudo generar el reto PKCE (SHA-256 no disponible).");
		}
	}

	/** Cookie name carrying the state + verifier across the redirect. */
	public static String cookieName(String state) {
		return "sisa.llave.flow." + state;
	}
}
