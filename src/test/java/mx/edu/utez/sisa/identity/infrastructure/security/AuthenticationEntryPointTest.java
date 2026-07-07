package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without a custom {@code AuthenticationEntryPoint}, Spring Security's
 * default for a stateless REST config returns 403 for BOTH "not
 * authenticated at all" and "authenticated but wrong role" — losing the
 * distinction a REST client needs to react correctly (e.g. force re-login
 * only on 401, not on every 403). Found via live browser verification of
 * the frontend's `apiClient.ts` 401-handling hook, which never fired
 * because the backend never actually sent a 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationEntryPointTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void missingBearerTokenMapsTo401() throws Exception {
		mockMvc.perform(get("/users"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/users"));
	}

	@Test
	void invalidSignatureBearerTokenMapsTo401() throws Exception {
		String tamperedToken = "eyJhbGciOiJIUzI1NiJ9."
				+ "eyJzdWIiOiJhOTc1NGVjMi01NjVmLTQxMDYtYTllOC1kMmViYzZjNjFjNjUiLCJyb2xlcyI6WyJBRE1JTiJdLCJleHAiOjk5OTk5OTk5OTl9."
				+ "not-a-real-signature";

		mockMvc.perform(get("/users").header("Authorization", "Bearer " + tamperedToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}
}
