package mx.edu.utez.sisa.shared.web;

import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code GET /states} — real H2, real seeded data
 * (the {@code StateAndMunicipalitySeedRunner} bootstrap already ran by the
 * time this test class's context starts), real JWT filter chain. Confirms
 * the {@code authenticated()}-only, no-role-restriction security matcher and
 * the exact 32-row seed count via the API itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StateControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Test
	void anyAuthenticatedRoleCanListAllThirtyTwoStates() throws Exception {
		String token = jwtService.sign(UUID.randomUUID().toString(), Set.of(RoleType.DOCENTE.name()));

		mockMvc.perform(get("/states").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(32));
	}

	@Test
	void listIncludesMorelosWithInegiCode17() throws Exception {
		String token = jwtService.sign(UUID.randomUUID().toString(), Set.of(RoleType.ADMIN.name()));

		mockMvc.perform(get("/states").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.inegiCode == '17')].name").value("Morelos"));
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/states")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}
}
