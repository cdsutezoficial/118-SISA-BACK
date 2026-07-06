package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {

	private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

	@Test
	void hashAndMatchesRoundTrip() {
		String rawPassword = "Secret123!";

		String hashed = hasher.hash(rawPassword);

		assertThat(hashed).isNotEqualTo(rawPassword);
		assertThat(hasher.matches(rawPassword, hashed)).isTrue();
		assertThat(hasher.matches("WrongPassword", hashed)).isFalse();
	}
}
