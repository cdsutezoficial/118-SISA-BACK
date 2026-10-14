package mx.edu.utez.sisa.identity.domain.port.out;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlaveMxPkceGeneratorTest {

	@Test
	void newCodeVerifier_isBase64UrlWithoutPadding() {
		String verifier = LlaveMxPkceGenerator.newCodeVerifier();

		assertThat(verifier).hasSize(43)
				.matches("[A-Za-z0-9_-]+")
				.doesNotContain("=");
	}

	@Test
	void newCodeVerifier_neverRepeats() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			seen.add(LlaveMxPkceGenerator.newCodeVerifier());
		}
		assertThat(seen).hasSize(500);
	}

	@Test
	void newState_neverRepeats() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			seen.add(LlaveMxPkceGenerator.newState());
		}
		assertThat(seen).hasSize(500);
	}

	@Test
	void newState_differsFromCodeVerifierAlphabet() {
		// The state travels through a cookie name, so it must stay free of the
		// characters that would need escaping there.
		assertThat(LlaveMxPkceGenerator.newState()).matches("[A-Za-z0-9_-]+");
	}

	/**
	 * The RFC 7636 appendix B worked example. If this ever changes, the provider
	 * rejects every login with an {@code invalid_grant} that points at SISA
	 * rather than at the hash.
	 */
	@Test
	void challengeOf_matchesRfc7636WorkedExample() {
		String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

		assertThat(LlaveMxPkceGenerator.challengeOf(verifier))
				.isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
	}

	@Test
	void challengeOf_isS256AndNotTheVerifierItself() throws Exception {
		String verifier = LlaveMxPkceGenerator.newCodeVerifier();

		String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
				MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

		assertThat(LlaveMxPkceGenerator.challengeOf(verifier)).isEqualTo(expected).isNotEqualTo(verifier);
	}

	@Test
	void cookieName_isScopedAndCarriesTheState() {
		assertThat(LlaveMxPkceGenerator.cookieName("abc123")).isEqualTo("sisa.llave.flow.abc123");
	}

	/**
	 * The callback looks the cookie up by state, so two tabs starting their own
	 * rounds must not collide on one cookie.
	 */
	@Test
	void cookieName_differsPerRound() {
		assertThat(LlaveMxPkceGenerator.cookieName(LlaveMxPkceGenerator.newState()))
				.isNotEqualTo(LlaveMxPkceGenerator.cookieName(LlaveMxPkceGenerator.newState()));
	}
}
