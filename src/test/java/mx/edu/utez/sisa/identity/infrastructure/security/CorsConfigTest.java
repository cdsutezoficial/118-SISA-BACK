package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the frontend dev server's origin can call the API cross-origin
 * (Vite runs on a different port than the backend), while other origins
 * cannot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "sisa.security.cors.allowed-origins=http://localhost:5173")
class CorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void preflightFromConfiguredOriginIsAllowed() throws Exception {
		mockMvc.perform(options("/auth/login").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void preflightFromUnconfiguredOriginIsRejected() throws Exception {
		mockMvc.perform(options("/auth/login").header("Origin", "http://evil.example.com")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden());
	}
}
