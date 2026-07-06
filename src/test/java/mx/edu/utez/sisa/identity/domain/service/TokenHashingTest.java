package mx.edu.utez.sisa.identity.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashingTest {

	@Test
	void sha256_isDeterministicForTheSameInput() {
		String hash1 = TokenHashing.sha256("opaque-refresh-token-value");
		String hash2 = TokenHashing.sha256("opaque-refresh-token-value");

		assertThat(hash1).isEqualTo(hash2);
	}

	@Test
	void sha256_producesDifferentHashesForDifferentInputs() {
		String hash1 = TokenHashing.sha256("token-a");
		String hash2 = TokenHashing.sha256("token-b");

		assertThat(hash1).isNotEqualTo(hash2);
	}
}
