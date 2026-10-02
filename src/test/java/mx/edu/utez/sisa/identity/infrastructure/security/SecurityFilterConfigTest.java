package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the {@code permitAll} contract of {@link SecurityFilterConfig} for the
 * password-recovery pair.
 *
 * <p>These two paths regressed once already: {@code aa22394} added them, then
 * merge {@code eebdf98} silently took {@code develop}'s side and dropped them,
 * leaving {@code POST /auth/forgot-password} answering 401 to the very callers
 * the flow exists for — a user who has no session because they cannot log in.
 * {@code AuthControllerTest} cannot catch this: it is a {@code @WebMvcTest} with
 * {@code addFilters = false}, so the whole authorization layer is bypassed. This
 * class runs the real chain for that reason.
 *
 * <p>The two assertions pair off the status codes deliberately: a 400 from
 * {@code /auth/reset-password} proves authorization <em>passed</em> and the
 * domain rejected the token, which is what distinguishes a working matcher from
 * a missing one (401) — the two failure modes are otherwise indistinguishable
 * from the client's point of view.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityFilterConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void forgotPasswordIsPublic() throws Exception {
		// 204 regardless of whether the account exists — the endpoint must never
		// reveal account existence, so an unknown username is still a success.
		mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"nadie@utez.edu.mx\"}"))
				.andExpect(status().isNoContent());
	}

	@Test
	void resetPasswordIsPublic() throws Exception {
		// The unknown token is rejected by ResetPasswordUseCaseImpl and mapped to
		// 400 by GlobalExceptionHandler — reaching the domain is the assertion.
		mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"token-inexistente\",\"newPassword\":\"NuevaClave1!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void changePasswordStaysAuthenticated() throws Exception {
		// The other /auth password endpoint is NOT public: it changes the caller's
		// own credential, so widening the matcher to "/auth/**" would be a breach.
		mockMvc.perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"Sup3rSecret!1\",\"newPassword\":\"NuevaClave1!\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meStaysAuthenticated() throws Exception {
		mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
	}
}